using System.Text.Json;
using MyMouse.HostCore.Phone;
using MyMouse.Protocol;

namespace MyMouse.HostCore.Shapes;

/// <summary>Una forma propia del móvil, con sus muestras normalizadas (0–65535).</summary>
public sealed record PhoneShape(string Name, IReadOnlyList<IReadOnlyList<StrokePoint>> Samples)
{
    public bool Equals(PhoneShape? other) =>
        other is not null && Name == other.Name && Samples.Count == other.Samples.Count &&
        Samples.Zip(other.Samples).All(p => p.First.SequenceEqual(p.Second));

    public override int GetHashCode() => HashCode.Combine(Name, Samples.Count);
}

/// <summary>
/// Formas propias del móvil en el PC (docs/spec-v4-toolbox.md §4.2 y §5): guarda la última lista recibida, con una copia
/// en <c>shapes-cache.json</c> para dibujar miniaturas sin conexión, detecta las formas nuevas y envía
/// <c>SHAPE_ADD</c> / <c>SHAPE_DELETE</c> esperando la respuesta del móvil. Los eventos llegan en el hilo de la sesión.
/// </summary>
public sealed class ShapeSyncService : IDisposable
{
    /// <summary>Cuánto se espera la respuesta del móvil a un SHAPE_ADD o SHAPE_DELETE.</summary>
    public static readonly TimeSpan ReplyTimeout = TimeSpan.FromSeconds(5);

    private readonly PhoneHub _hub;
    private readonly string? _cachePath;
    private readonly TimeProvider _time;
    private readonly object _lock = new();
    private readonly Dictionary<string, TaskCompletionSource<ShapeResultMessage?>> _pending = new(StringComparer.Ordinal);
    private readonly HashSet<string> _addedHere = new(StringComparer.Ordinal);
    private List<PhoneShape> _shapes = [];
    private List<PhoneShape>? _incoming;
    private bool _synced;

    public ShapeSyncService(PhoneHub hub, string? cachePath = null, TimeProvider? time = null)
    {
        _hub = hub;
        _cachePath = cachePath;
        _time = time ?? TimeProvider.System;
        LoadCache();
        _hub.Received += OnReceived;
        _hub.Changed += OnHubChanged;
    }

    /// <summary>Formas propias conocidas (la última lista del móvil o, sin conexión, la copia guardada).</summary>
    public IReadOnlyList<PhoneShape> Shapes
    {
        get { lock (_lock) return [.. _shapes]; }
    }

    /// <summary>¿Hay un móvil v5 conectado? Si no, no se pueden grabar ni borrar formas.</summary>
    public bool CanEdit => _hub.SupportsToolbox;

    /// <summary>Cambió la lista de formas o la conexión.</summary>
    public event Action? Changed;

    /// <summary>Llega una forma propia que el PC no conocía (no se avisa de las que se crean desde el PC ni en la primera sincronización).</summary>
    public event Action<PhoneShape>? NewShape;

    public bool Contains(string name)
    {
        lock (_lock) return _shapes.Any(s => s.Name == name);
    }

    /// <summary>
    /// Pide al móvil que guarde una forma. Devuelve su respuesta, o null si no hay móvil, se cortó la conexión o no contestó a tiempo.
    /// </summary>
    public Task<ShapeResultMessage?> AddAsync(string name, IReadOnlyList<IReadOnlyList<StrokePoint>> samples, bool force = false,
        CancellationToken cancellationToken = default) =>
        RequestAsync(name, new ShapeAddMessage(name, samples, force), cancellationToken);

    /// <summary>Pide al móvil que borre una forma propia. Misma respuesta que <see cref="AddAsync"/>.</summary>
    public Task<ShapeResultMessage?> DeleteAsync(string name, CancellationToken cancellationToken = default) =>
        RequestAsync(name, new ShapeDeleteMessage(name), cancellationToken);

    private async Task<ShapeResultMessage?> RequestAsync(string name, Message request, CancellationToken cancellationToken)
    {
        if (!_hub.SupportsToolbox) return null;
        var tcs = new TaskCompletionSource<ShapeResultMessage?>(TaskCreationOptions.RunContinuationsAsynchronously);
        lock (_lock)
        {
            // Una petición nueva para el mismo nombre sustituye a la anterior.
            if (_pending.Remove(name, out var old)) old.TrySetResult(null);
            _pending[name] = tcs;
        }

        try
        {
            if (!await _hub.SendAsync(request, cancellationToken).ConfigureAwait(false)) return null;
            using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
            var delay = Task.Delay(ReplyTimeout, _time, timeout.Token);
            var done = await Task.WhenAny(tcs.Task, delay).ConfigureAwait(false);
            if (done == tcs.Task)
            {
                await timeout.CancelAsync().ConfigureAwait(false);
                return await tcs.Task.ConfigureAwait(false);
            }
            return null;
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
            return null;
        }
        finally
        {
            lock (_lock)
            {
                if (_pending.TryGetValue(name, out var current) && current == tcs) _pending.Remove(name);
            }
        }
    }

    private void OnReceived(Message message)
    {
        switch (message)
        {
            case ShapeInfoMessage info:
                lock (_lock)
                {
                    _incoming ??= [];
                    _incoming.RemoveAll(s => s.Name == info.Name);
                    _incoming.Add(new PhoneShape(info.Name, info.Samples));
                }
                break;
            case ShapeListEndMessage:
                CompleteList();
                break;
            case ShapeResultMessage result:
                lock (_lock)
                {
                    if (result.Status == ShapeStatus.Saved) _addedHere.Add(result.Name);
                    if (_pending.Remove(result.Name, out var tcs)) tcs.TrySetResult(result);
                }
                break;
        }
    }

    private void CompleteList()
    {
        List<PhoneShape> list;
        List<PhoneShape> added = [];
        lock (_lock)
        {
            list = _incoming ?? [];
            _incoming = null;
            if (_synced)
            {
                var known = _shapes.Select(s => s.Name).ToHashSet(StringComparer.Ordinal);
                added = [.. list.Where(s => !known.Contains(s.Name) && !_addedHere.Contains(s.Name))];
            }
            _addedHere.Clear();
            _shapes = list;
            _synced = true;
        }
        SaveCache(list);
        Changed?.Invoke();
        foreach (var shape in added) NewShape?.Invoke(shape);
    }

    private void OnHubChanged()
    {
        List<TaskCompletionSource<ShapeResultMessage?>> orphaned;
        lock (_lock)
        {
            _incoming = null; // una lista a medias de la conexión anterior no vale
            if (_hub.SupportsToolbox)
            {
                orphaned = [];
            }
            else
            {
                orphaned = [.. _pending.Values];
                _pending.Clear();
            }
        }
        foreach (var tcs in orphaned) tcs.TrySetResult(null);
        Changed?.Invoke();
    }

    // ---- Copia en disco ----

    private void LoadCache()
    {
        if (_cachePath is null || !File.Exists(_cachePath)) return;
        try
        {
            using var doc = JsonDocument.Parse(File.ReadAllText(_cachePath));
            var list = new List<PhoneShape>();
            foreach (var item in doc.RootElement.GetProperty("shapes").EnumerateArray())
            {
                var samples = new List<IReadOnlyList<StrokePoint>>();
                foreach (var s in item.GetProperty("samples").EnumerateArray())
                {
                    var flat = s.EnumerateArray().Select(v => v.GetInt32()).ToList();
                    samples.Add([.. Enumerable.Range(0, flat.Count / 2)
                        .Select(i => new StrokePoint((ushort)flat[2 * i], (ushort)flat[2 * i + 1]))]);
                }
                list.Add(new PhoneShape(item.GetProperty("name").GetString() ?? "", samples));
            }
            _shapes = list;
            _synced = true;
        }
        catch (Exception ex) when (ex is JsonException or IOException or UnauthorizedAccessException or KeyNotFoundException
                                       or InvalidOperationException or FormatException or OverflowException)
        {
            // Copia ilegible: se vuelve a crear en la próxima sincronización.
            _shapes = [];
            _synced = false;
        }
    }

    private void SaveCache(IReadOnlyList<PhoneShape> list)
    {
        if (_cachePath is null) return;
        try
        {
            Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(_cachePath))!);
            using var stream = new MemoryStream();
            using (var w = new Utf8JsonWriter(stream, new JsonWriterOptions { Indented = true }))
            {
                w.WriteStartObject();
                w.WriteStartArray("shapes");
                foreach (var shape in list)
                {
                    w.WriteStartObject();
                    w.WriteString("name", shape.Name);
                    w.WriteStartArray("samples");
                    foreach (var sample in shape.Samples)
                    {
                        w.WriteStartArray();
                        foreach (var p in sample)
                        {
                            w.WriteNumberValue(p.X);
                            w.WriteNumberValue(p.Y);
                        }
                        w.WriteEndArray();
                    }
                    w.WriteEndArray();
                    w.WriteEndObject();
                }
                w.WriteEndArray();
                w.WriteEndObject();
            }
            var temp = _cachePath + ".tmp";
            File.WriteAllBytes(temp, stream.ToArray());
            File.Move(temp, _cachePath, overwrite: true);
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            // Solo es una copia para trabajar sin conexión: si no se puede guardar, no pasa nada.
        }
    }

    public void Dispose()
    {
        _hub.Received -= OnReceived;
        _hub.Changed -= OnHubChanged;
    }
}
