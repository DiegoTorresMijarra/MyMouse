using Microsoft.Extensions.Time.Testing;
using MyMouse.HostCore.Live;
using MyMouse.HostCore.Shapes;
using MyMouse.Protocol;

namespace MyMouse.HostCore.Tests;

public class LiveStrokeModelTests
{
    private readonly FakeTimeProvider _time = new();
    private readonly LiveStrokeModel _model;
    private int _changes;

    public LiveStrokeModelTests()
    {
        _model = new LiveStrokeModel(_time);
        _model.Changed += () => _changes++;
    }

    private static StrokePoint P(int x, int y) => new((ushort)x, (ushort)y);

    private void Draw()
    {
        _model.OnGestureMode(true, TimeSpan.FromSeconds(3));
        _model.OnPoints([P(0, 0), P(32768, 32768)]);
        _model.OnPoints([P(65535, 65535)]);
    }

    [Fact]
    public void ActivatingShowsTheCardWaiting_WithACountdown()
    {
        Assert.False(_model.Visible);
        _model.OnGestureMode(true, TimeSpan.FromSeconds(3));
        Assert.Equal(LivePhase.Waiting, _model.Phase);
        Assert.Equal(1.0, _model.WaitRemaining(), 3);
        _time.Advance(TimeSpan.FromSeconds(1.5));
        Assert.Equal(0.5, _model.WaitRemaining(), 3);
        _time.Advance(TimeSpan.FromSeconds(10));
        Assert.Equal(0, _model.WaitRemaining());
    }

    [Fact]
    public void PointsAccumulateAsFractions()
    {
        Draw();
        Assert.Equal(LivePhase.Drawing, _model.Phase);
        Assert.Equal(3, _model.Points.Count);
        Assert.Equal(new PointF(0.5f, 0.5f), _model.Points[1], new PointComparer());
        Assert.Equal(1f, _model.Points[2].X);
    }

    private sealed class PointComparer : IEqualityComparer<PointF>
    {
        public bool Equals(PointF a, PointF b) => Math.Abs(a.X - b.X) < 1e-3 && Math.Abs(a.Y - b.Y) < 1e-3;
        public int GetHashCode(PointF p) => 0;
    }

    [Fact]
    public void RecognizedWithAction_IsGreen_AndClosesAfter1500ms()
    {
        Draw();
        _model.OnStrokeEnd(StrokeResult.Recognized);
        Assert.Equal(LiveResultKind.Pending, _model.Result.Kind);
        _model.OnOutcome(GestureStatus.Executed, "circle", "Abrir Chrome", "launch chrome.exe", null);

        Assert.Equal(LivePhase.Finished, _model.Phase);
        Assert.Equal(LiveResultKind.Executed, _model.Result.Kind);
        Assert.Equal("Abrir Chrome", _model.Result.Help);
        Assert.False(_model.CanAssign);

        _time.Advance(TimeSpan.FromMilliseconds(1400));
        _model.Tick();
        Assert.True(_model.Visible);
        _time.Advance(TimeSpan.FromMilliseconds(150));
        _model.Tick();
        Assert.False(_model.Visible);
        Assert.Empty(_model.Points);
    }

    [Fact]
    public void RecognizedWithoutAction_OffersAssign_AndStaysFor2500ms()
    {
        Draw();
        _model.OnStrokeEnd(StrokeResult.Recognized);
        _model.OnOutcome(GestureStatus.Unassigned, "custom:firma", null, null, null);
        Assert.Equal(LiveResultKind.NoAction, _model.Result.Kind);
        Assert.True(_model.CanAssign);

        _time.Advance(TimeSpan.FromMilliseconds(2400));
        _model.Tick();
        Assert.True(_model.Visible);
        _time.Advance(TimeSpan.FromMilliseconds(150));
        _model.Tick();
        Assert.False(_model.Visible);
    }

    [Theory]
    [InlineData(StrokeResult.NotRecognized, LiveResultKind.NotRecognized)]
    [InlineData(StrokeResult.Cancelled, LiveResultKind.Cancelled)]
    [InlineData(StrokeResult.TimedOut, LiveResultKind.Cancelled)]
    public void OtherResults(StrokeResult end, LiveResultKind expected)
    {
        Draw();
        _model.OnStrokeEnd(end);
        Assert.Equal(expected, _model.Result.Kind);
        Assert.Equal(LivePhase.Finished, _model.Phase);
    }

    [Fact]
    public void FailedAction_ShowsTheError()
    {
        Draw();
        _model.OnStrokeEnd(StrokeResult.Recognized);
        _model.OnOutcome(GestureStatus.Error, "z", "Deshacer", "keys Ctrl+Z", "Windows no aceptó las teclas");
        Assert.Equal(LiveResultKind.Failed, _model.Result.Kind);
        Assert.Equal("Windows no aceptó las teclas", _model.Result.Error);
    }

    [Fact]
    public void RecognizedWithoutOutcome_BecomesAnError_NotAFakeSuccess()
    {
        Draw();
        _model.OnStrokeEnd(StrokeResult.Recognized);
        _time.Advance(LiveStrokeModel.PendingTimeout + TimeSpan.FromMilliseconds(1));
        _model.Tick();
        Assert.Equal(LiveResultKind.Failed, _model.Result.Kind);
        Assert.Contains("pausa", _model.Result.Error);
    }

    [Fact]
    public void ReactivatingWhileTheResultIsShown_ReusesTheCard()
    {
        Draw();
        _model.OnStrokeEnd(StrokeResult.NotRecognized);
        var generation = _model.Generation;
        _model.OnGestureMode(true, TimeSpan.FromSeconds(3));
        Assert.True(_model.Visible); // no se ha cerrado
        Assert.Equal(LivePhase.Waiting, _model.Phase);
        Assert.Empty(_model.Points);
        Assert.Equal(LiveResultKind.None, _model.Result.Kind);
        Assert.Equal(generation + 1, _model.Generation);

        // El cierre pendiente del resultado anterior ya no vale.
        _time.Advance(TimeSpan.FromSeconds(5));
        _model.Tick();
        Assert.True(_model.Visible);
    }

    [Fact]
    public void LeavingTheModeWithoutAStrokeEnd_CountsAsCancelled()
    {
        _model.OnGestureMode(true, TimeSpan.FromSeconds(3));
        _model.OnGestureMode(false, TimeSpan.Zero);
        Assert.Equal(LiveResultKind.Cancelled, _model.Result.Kind);

        // Pero tras un resultado, el fin del modo no lo cambia.
        Draw();
        _model.OnStrokeEnd(StrokeResult.NotRecognized);
        _model.OnGestureMode(false, TimeSpan.Zero);
        Assert.Equal(LiveResultKind.NotRecognized, _model.Result.Kind);
    }

    [Fact]
    public void StrayMessagesWhileHidden_AreIgnored()
    {
        _model.OnPoints([P(1, 1)]);
        _model.OnStrokeEnd(StrokeResult.Recognized);
        _model.OnOutcome(GestureStatus.Executed, "z", null, null, null);
        Assert.False(_model.Visible);
        Assert.Equal(0, _changes);
    }

    [Fact]
    public void Hide_ClosesImmediately()
    {
        Draw();
        _model.OnStrokeEnd(StrokeResult.Recognized);
        _model.OnOutcome(GestureStatus.Unassigned, "custom:x", null, null, null);
        _model.Hide();
        Assert.False(_model.Visible);
    }

    [Fact]
    public void Fit_KeepsProportionsAndCenters()
    {
        // Un trazo ancho (1 × 0,5) en un lienzo de 200 × 100 con margen 10.
        var pts = new PointF[] { new(0f, 0f), new(1f, 0.5f) };
        var fit = LiveStrokeModel.ComputeFit(pts, 200, 100, 10);
        Assert.Equal(160f, fit.Scale, 3); // manda el alto: 80 / 0,5 (el ancho daría 180)
        // Centro del trazo (0,5; 0,25) → centro del lienzo (100; 50).
        Assert.Equal(100f, 0.5f * fit.Scale + fit.OffsetX, 3);
        Assert.Equal(50f, 0.25f * fit.Scale + fit.OffsetY, 3);
    }

    [Fact]
    public void Fit_OfAFewPoints_DoesNotBlowUp()
    {
        var fit = LiveStrokeModel.ComputeFit([new PointF(0.5f, 0.5f), new PointF(0.51f, 0.5f)], 200, 200, 0);
        Assert.Equal(200f / LiveStrokeModel.MinFitExtent, fit.Scale, 2);
        Assert.Equal(100f, 0.505f * fit.Scale + fit.OffsetX, 2);
    }

    [Fact]
    public void Fit_OfNoPoints_IsTheWholeCanvas()
    {
        var fit = LiveStrokeModel.ComputeFit([], 200, 100, 10);
        Assert.Equal(80f, fit.Scale);
    }

    [Fact]
    public void ModeNotice_ShowsTheCardBrieflyAndClosesItself()
    {
        _model.ShowNotice("⌨ Teclado", "Escribe con el teclado del móvil");
        Assert.True(_model.Visible);
        Assert.Equal(LivePhase.Finished, _model.Phase);
        Assert.Equal(new LiveResult(LiveResultKind.Notice, Help: "⌨ Teclado", Action: "Escribe con el teclado del móvil"), _model.Result);
        Assert.False(_model.CanAssign);
        Assert.Empty(_model.Points);

        _time.Advance(LiveStrokeModel.NoticeDelay - TimeSpan.FromMilliseconds(1));
        _model.Tick();
        Assert.True(_model.Visible);
        _time.Advance(TimeSpan.FromMilliseconds(2));
        _model.Tick();
        Assert.False(_model.Visible);
    }

    [Fact]
    public void ModeNotice_DoesNotInterruptAGestureInProgress()
    {
        Draw();
        _model.ShowNotice("🖱 Mouse");
        Assert.Equal(LivePhase.Drawing, _model.Phase);
        Assert.Equal(3, _model.Points.Count);
    }

    [Fact]
    public void ModeNotice_ReplacesAnOldResult_AndAGestureCanFollow()
    {
        Draw();
        _model.OnStrokeEnd(StrokeResult.NotRecognized);
        _model.ShowNotice("⌨ Teclado");
        Assert.Equal(LiveResultKind.Notice, _model.Result.Kind);
        _model.OnGestureMode(true, TimeSpan.FromSeconds(3));
        Assert.Equal(LivePhase.Waiting, _model.Phase);
        Assert.Equal(LiveResult.None, _model.Result);
    }
}
