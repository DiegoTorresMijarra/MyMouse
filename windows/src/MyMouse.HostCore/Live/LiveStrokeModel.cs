using MyMouse.HostCore.Shapes;
using MyMouse.Protocol;

namespace MyMouse.HostCore.Live;

public enum LivePhase
{
    /// <summary>La tarjeta no se ve.</summary>
    Hidden,

    /// <summary>Modo gestos activado: se espera a que empiece el trazo.</summary>
    Waiting,

    /// <summary>Se está dibujando.</summary>
    Drawing,

    /// <summary>Trazo terminado: se muestra el resultado hasta que toque cerrar.</summary>
    Finished,
}

public enum LiveResultKind
{
    /// <summary>Aún sin resultado.</summary>
    None,

    /// <summary>Trazo reconocido; falta saber qué pasó al ejecutar su acción.</summary>
    Pending,

    /// <summary>Reconocida y ejecutada (verde).</summary>
    Executed,

    /// <summary>Reconocida, pero sin acción (ámbar, con botón "Asignar").</summary>
    NoAction,

    /// <summary>No reconocida (gris).</summary>
    NotRecognized,

    /// <summary>Cancelada o tiempo agotado.</summary>
    Cancelled,

    /// <summary>La acción falló (rojo).</summary>
    Failed,

    /// <summary>Aviso de cambio de modo del móvil ("⌨ Teclado", "🖱 Mouse"): título en <c>Help</c> y detalle en <c>Action</c>.</summary>
    Notice,
}

/// <summary>Lo que se muestra al terminar el trazo. <see cref="Shape"/>: nombre de la forma reconocida.</summary>
public sealed record LiveResult(LiveResultKind Kind, string? Shape = null, string? Help = null, string? Action = null, string? Error = null)
{
    public static LiveResult None { get; } = new(LiveResultKind.None);
}

/// <summary>Cómo encajar el trazo en el lienzo: <c>x' = x * Scale + OffsetX</c> (y en y).</summary>
public readonly record struct StrokeFit(float Scale, float OffsetX, float OffsetY);

/// <summary>
/// Modelo del gesto en vivo (docs/spec-v4-toolbox.md §3): acumula los puntos que llegan del móvil, calcula el encaje en
/// el lienzo y lleva el estado (esperando, dibujando, resultado) y los tiempos de cierre. No sabe nada de ventanas:
/// la vista lo lee y se anima. Se usa desde un solo hilo (el de la interfaz).
/// </summary>
public sealed class LiveStrokeModel(TimeProvider? time = null)
{
    /// <summary>Tiempo que se ve el resultado antes de cerrar la tarjeta.</summary>
    public static readonly TimeSpan CloseDelay = TimeSpan.FromSeconds(1.5);

    /// <summary>Si hay botón "Asignar", se deja más tiempo para pulsarlo.</summary>
    public static readonly TimeSpan CloseDelayWithAssign = TimeSpan.FromSeconds(2.5);

    /// <summary>Si la acción no ha contestado a tiempo (PC en pausa, por ejemplo), el resultado pendiente pasa a error.</summary>
    public static readonly TimeSpan PendingTimeout = TimeSpan.FromMilliseconds(700);

    /// <summary>Lado mínimo (en 0–1) de la caja del trazo al encajarlo, para que los primeros puntos no se amplíen sin medida.</summary>
    public const float MinFitExtent = 0.25f;

    private readonly TimeProvider _time = time ?? TimeProvider.System;
    private readonly List<PointF> _points = [];
    private DateTimeOffset _waitStarted;
    private DateTimeOffset? _closeAt;
    private DateTimeOffset _finishedAt;

    public LivePhase Phase { get; private set; } = LivePhase.Hidden;

    public bool Visible => Phase != LivePhase.Hidden;

    public LiveResult Result { get; private set; } = LiveResult.None;

    /// <summary>Puntos del trazo, en 0–1 (misma escala en los dos ejes).</summary>
    public IReadOnlyList<PointF> Points => _points;

    /// <summary>Tiempo que da el móvil para empezar a dibujar.</summary>
    public TimeSpan StartTimeout { get; private set; }

    /// <summary>Se incrementa cada vez que empieza un modo gestos: la vista sabe que debe vaciar el lienzo.</summary>
    public int Generation { get; private set; }

    /// <summary>¿Debe mostrarse el botón "Asignar"?</summary>
    public bool CanAssign => Result.Kind == LiveResultKind.NoAction;

    /// <summary>Algo ha cambiado (fase, puntos o resultado).</summary>
    public event Action? Changed;

    /// <summary>Fracción de la cuenta atrás que queda para empezar a dibujar (1 → 0). 0 si no se está esperando.</summary>
    public double WaitRemaining()
    {
        if (Phase != LivePhase.Waiting || StartTimeout <= TimeSpan.Zero) return 0;
        var elapsed = _time.GetUtcNow() - _waitStarted;
        return Math.Clamp(1 - elapsed / StartTimeout, 0, 1);
    }

    /// <summary><c>GESTURE_MODE</c>. Si la tarjeta aún se ve (resultado anterior), se reutiliza sin cerrarla.</summary>
    public void OnGestureMode(bool active, TimeSpan startTimeout)
    {
        if (active)
        {
            _points.Clear();
            Result = LiveResult.None;
            _closeAt = null;
            StartTimeout = startTimeout;
            _waitStarted = _time.GetUtcNow();
            Phase = LivePhase.Waiting;
            Generation++;
        }
        else if (Phase is LivePhase.Waiting or LivePhase.Drawing)
        {
            // El móvil salió del modo sin avisar del final del trazo: cuenta como cancelado.
            Finish(new LiveResult(LiveResultKind.Cancelled), CloseDelay);
            return;
        }
        else
        {
            return;
        }
        Changed?.Invoke();
    }

    /// <summary><c>STROKE_POINTS</c>: puntos nuevos (0–65535).</summary>
    public void OnPoints(IReadOnlyList<StrokePoint> points)
    {
        if (Phase == LivePhase.Hidden || Phase == LivePhase.Finished) return;
        foreach (var p in points) _points.Add(new PointF(p.X / 65535f, p.Y / 65535f));
        Phase = LivePhase.Drawing;
        Changed?.Invoke();
    }

    /// <summary><c>STROKE_END</c>. Con "reconocida" el resultado queda pendiente de <see cref="OnOutcome"/>.</summary>
    public void OnStrokeEnd(StrokeResult result)
    {
        if (Phase == LivePhase.Hidden) return;
        switch (result)
        {
            case StrokeResult.Recognized:
                Finish(new LiveResult(LiveResultKind.Pending), CloseDelay);
                break;
            case StrokeResult.NotRecognized:
                Finish(new LiveResult(LiveResultKind.NotRecognized), CloseDelay);
                break;
            default: // cancelada o tiempo agotado
                Finish(new LiveResult(LiveResultKind.Cancelled), CloseDelay);
                break;
        }
    }

    /// <summary>Qué pasó al ejecutar la acción de la forma reconocida.</summary>
    public void OnOutcome(GestureStatus status, string shape, string? help, string? action, string? error)
    {
        if (Phase != LivePhase.Finished) return;
        var (kind, delay) = status switch
        {
            GestureStatus.Executed => (LiveResultKind.Executed, CloseDelay),
            GestureStatus.Unassigned => (LiveResultKind.NoAction, CloseDelayWithAssign),
            _ => (LiveResultKind.Failed, CloseDelay),
        };
        Finish(new LiveResult(kind, shape, help, action, error), delay);
    }

    /// <summary>Tiempo que se ve el aviso de cambio de modo.</summary>
    public static readonly TimeSpan NoticeDelay = TimeSpan.FromSeconds(1.2);

    /// <summary>
    /// Aviso corto de cambio de modo (<c>KEYBOARD_MODE</c>: "⌨ Teclado" al entrar y "🖱 Mouse" al salir). Usa la misma tarjeta y animación
    /// que el gesto en vivo; si hay un gesto en curso, no lo interrumpe.
    /// </summary>
    public void ShowNotice(string headline, string? detail = null)
    {
        if (Phase is LivePhase.Waiting or LivePhase.Drawing) return;
        _points.Clear();
        Generation++;
        Finish(new LiveResult(LiveResultKind.Notice, Help: headline, Action: detail), NoticeDelay);
    }

    /// <summary>Hay que avisar a la vista (cada ~100 ms mientras sea visible): cierra la tarjeta si ya toca y resuelve lo pendiente.</summary>
    public void Tick()
    {
        var now = _time.GetUtcNow();
        if (Phase == LivePhase.Finished && Result.Kind == LiveResultKind.Pending && now - _finishedAt >= PendingTimeout)
        {
            Finish(new LiveResult(LiveResultKind.Failed, Error: CoreLoc.T("lsm_not_run")), CloseDelay);
            return;
        }
        if (Phase == LivePhase.Finished && _closeAt is { } at && now >= at) Hide();
    }

    /// <summary>Cierra la tarjeta ya (p. ej. al pulsar "Asignar" o al desconectar el móvil).</summary>
    public void Hide()
    {
        if (Phase == LivePhase.Hidden) return;
        Phase = LivePhase.Hidden;
        _closeAt = null;
        _points.Clear();
        Result = LiveResult.None;
        Changed?.Invoke();
    }

    private void Finish(LiveResult result, TimeSpan delay)
    {
        var now = _time.GetUtcNow();
        if (Phase != LivePhase.Finished) _finishedAt = now;
        Phase = LivePhase.Finished;
        Result = result;
        _closeAt = now + delay;
        Changed?.Invoke();
    }

    /// <summary>
    /// Escala y desplazamiento para dibujar los puntos dentro de un lienzo de <paramref name="width"/> × <paramref name="height"/>
    /// con un margen <paramref name="padding"/>, conservando las proporciones y centrados. Con pocos puntos se usa como
    /// mínimo <see cref="MinFitExtent"/> para que el trazo no salte al ampliarse.
    /// </summary>
    public static StrokeFit ComputeFit(IReadOnlyList<PointF> points, float width, float height, float padding)
    {
        var innerW = Math.Max(1f, width - 2 * padding);
        var innerH = Math.Max(1f, height - 2 * padding);
        if (ShapeGeometry.Bounds(points) is not { } b)
            return new StrokeFit(Math.Min(innerW, innerH), padding, padding);

        var w = Math.Max(b.MaxX - b.MinX, MinFitExtent);
        var h = Math.Max(b.MaxY - b.MinY, MinFitExtent);
        var scale = Math.Min(innerW / w, innerH / h);
        var cx = (b.MinX + b.MaxX) / 2;
        var cy = (b.MinY + b.MaxY) / 2;
        return new StrokeFit(scale, width / 2 - cx * scale, height / 2 - cy * scale);
    }
}
