using Microsoft.Extensions.Time.Testing;
using MyMouse.HostCore.Phone;
using MyMouse.HostCore.Shapes;
using MyMouse.Protocol;

namespace MyMouse.HostCore.Tests;

public class ShapeSyncServiceTests : IDisposable
{
    private readonly string _dir = Directory.CreateTempSubdirectory("mymouse-shapes").FullName;
    private readonly PhoneHub _hub = new();
    private readonly List<Message> _sent = [];
    private IDisposable? _attachment;

    private string Cache => Path.Combine(_dir, "shapes-cache.json");

    public void Dispose() => Directory.Delete(_dir, recursive: true);

    private static IReadOnlyList<StrokePoint> Sample(int seed) =>
        [.. Enumerable.Range(0, 32).Select(i => new StrokePoint((ushort)(i * 1000 + seed), (ushort)(65535 - i * 1000)))];

    private static ShapeInfoMessage Info(string name, int seed = 0) => new(name, [Sample(seed), Sample(seed + 1), Sample(seed + 2)]);

    private void Connect(byte version = 5) =>
        _attachment = _hub.Attach(version, (m, _) => { _sent.Add(m); return ValueTask.CompletedTask; });

    private void Disconnect() => _attachment?.Dispose();

    private void Send(params ShapeInfoMessage[] shapes)
    {
        foreach (var s in shapes) _hub.Dispatch(s);
        _hub.Dispatch(new ShapeListEndMessage((byte)shapes.Length));
    }

    [Fact]
    public void List_ReplacesTheShapes_AndRaisesChanged()
    {
        using var svc = new ShapeSyncService(_hub, Cache);
        var changed = 0;
        svc.Changed += () => changed++;
        Connect();
        var before = changed;

        Send(Info("custom:a"), Info("custom:b", 5));
        Assert.Equal(["custom:a", "custom:b"], svc.Shapes.Select(s => s.Name));
        Assert.Equal(before + 1, changed);

        Send(Info("custom:b", 5));
        Assert.Equal(["custom:b"], svc.Shapes.Select(s => s.Name));
        Assert.True(svc.Contains("custom:b"));
        Assert.False(svc.Contains("custom:a"));
    }

    [Fact]
    public void Cache_IsWrittenAndReadBack()
    {
        using (var svc = new ShapeSyncService(_hub, Cache))
        {
            Connect();
            Send(Info("custom:café"), Info("custom:b", 5));
        }
        Assert.True(File.Exists(Cache));

        using var offline = new ShapeSyncService(new PhoneHub(), Cache);
        Assert.Equal(["custom:café", "custom:b"], offline.Shapes.Select(s => s.Name));
        Assert.Equal(Info("custom:b", 5).Samples, offline.Shapes[1].Samples);
        Assert.False(offline.CanEdit); // sin móvil: se ven, pero no se pueden grabar
    }

    [Fact]
    public void CorruptCache_IsIgnored()
    {
        File.WriteAllText(Cache, "{ esto no es json");
        using var svc = new ShapeSyncService(_hub, Cache);
        Assert.Empty(svc.Shapes);
        Connect();
        Send(Info("custom:a"));
        Assert.Single(svc.Shapes);
    }

    [Fact]
    public void NewShapes_AreReported_ExceptTheFirstSyncAndTheOnesAddedHere()
    {
        using var svc = new ShapeSyncService(_hub, Cache);
        var added = new List<string>();
        svc.NewShape += s => added.Add(s.Name);
        Connect();

        Send(Info("custom:a")); // primera sincronización: no se avisa
        Assert.Empty(added);

        Send(Info("custom:a"), Info("custom:b", 3)); // grabada en el móvil
        Assert.Equal(["custom:b"], added);

        // La que se crea en el PC llega con SHAPE_RESULT guardada y luego la lista: no se avisa.
        _hub.Dispatch(new ShapeResultMessage("custom:c", ShapeStatus.Saved, ""));
        Send(Info("custom:a"), Info("custom:b", 3), Info("custom:c", 9));
        Assert.Equal(["custom:b"], added);
    }

    [Fact]
    public void NewShape_AfterRestartWithCache_IsReported()
    {
        using (var svc = new ShapeSyncService(_hub, Cache))
        {
            Connect();
            Send(Info("custom:a"));
        }
        Disconnect();

        using var again = new ShapeSyncService(_hub, Cache);
        var added = new List<string>();
        again.NewShape += s => added.Add(s.Name);
        Connect();
        Send(Info("custom:a"), Info("custom:z", 4));
        Assert.Equal(["custom:z"], added);
    }

    [Fact]
    public async Task Add_SendsTheMessage_AndReturnsTheReply()
    {
        using var svc = new ShapeSyncService(_hub, Cache);
        Connect();
        var samples = new List<IReadOnlyList<StrokePoint>> { Sample(0), Sample(1), Sample(2) };

        var task = svc.AddAsync("custom:a", samples, force: true);
        await Task.Yield();
        Assert.Equal(new ShapeAddMessage("custom:a", samples, true), Assert.Single(_sent));

        _hub.Dispatch(new ShapeResultMessage("custom:a", ShapeStatus.Clashes, "z"));
        var reply = await task;
        Assert.Equal(new ShapeResultMessage("custom:a", ShapeStatus.Clashes, "z"), reply);
    }

    [Fact]
    public async Task Delete_ReturnsTheReply()
    {
        using var svc = new ShapeSyncService(_hub, Cache);
        Connect();
        var task = svc.DeleteAsync("custom:a");
        await Task.Yield();
        Assert.Equal(new ShapeDeleteMessage("custom:a"), Assert.Single(_sent));
        _hub.Dispatch(new ShapeResultMessage("custom:a", ShapeStatus.Deleted, ""));
        Assert.Equal(ShapeStatus.Deleted, (await task)!.Status);
    }

    [Fact]
    public async Task Requests_WithoutAV5Phone_ReturnNull()
    {
        using var svc = new ShapeSyncService(_hub, Cache);
        Assert.Null(await svc.AddAsync("custom:a", [Sample(0)]));
        Connect(version: 5);
        Disconnect();
        Assert.Null(await svc.DeleteAsync("custom:a"));
    }

    [Fact]
    public async Task Request_TimesOut_WhenThePhoneDoesNotAnswer()
    {
        var time = new FakeTimeProvider();
        using var svc = new ShapeSyncService(_hub, Cache, time);
        Connect();
        var task = svc.AddAsync("custom:a", [Sample(0)]);
        await Task.Yield();
        Assert.False(task.IsCompleted);
        time.Advance(ShapeSyncService.ReplyTimeout + TimeSpan.FromMilliseconds(1));
        Assert.Null(await task.WaitAsync(TimeSpan.FromSeconds(5)));
    }

    [Fact]
    public async Task Request_IsCancelled_WhenThePhoneDisconnects()
    {
        using var svc = new ShapeSyncService(_hub, Cache);
        Connect();
        var task = svc.AddAsync("custom:a", [Sample(0)]);
        await Task.Yield();
        Disconnect();
        Assert.Null(await task.WaitAsync(TimeSpan.FromSeconds(5)));
    }

    [Fact]
    public void AListCutByADisconnect_IsDiscarded()
    {
        using var svc = new ShapeSyncService(_hub, Cache);
        Connect();
        Send(Info("custom:a"));
        _hub.Dispatch(Info("custom:x")); // lista a medias
        Disconnect();
        Connect();
        Send(Info("custom:a"), Info("custom:b", 2));
        Assert.Equal(["custom:a", "custom:b"], svc.Shapes.Select(s => s.Name));
    }
}
