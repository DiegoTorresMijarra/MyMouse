using MyMouse.HostCore.Gestures;
using MyMouse.Protocol;

namespace MyMouse.HostCore.Widgets;

/// <summary>
/// Implementación de <see cref="IWidgetService"/>: la lista de widgets viene de la configuración
/// (<see cref="SetWidgets"/>); el volumen y la música, de sus controladores. Los avisos de cambio
/// de estado se limitan a uno cada 100 ms y el último valor siempre llega.
/// </summary>
public sealed class WidgetService : IWidgetService, IDisposable
{
    /// <summary>Separación mínima entre dos <c>VOLUME_STATE</c> (o dos <c>MEDIA_STATE</c>) seguidos.</summary>
    public static readonly TimeSpan MinStateInterval = TimeSpan.FromMilliseconds(100);

    private readonly IActionExecutor _executor;
    private readonly IVolumeController _volume;
    private readonly IMediaController _media;
    private readonly RateLimiter<VolumeStateMessage> _volumeOut;
    private readonly RateLimiter<MediaStateMessage> _mediaOut;
    private readonly object _lock = new();
    private IReadOnlyList<WidgetDefinition> _widgets = [];
    private VolumeStateMessage? _lastVolume;
    private MediaStateMessage? _lastMedia;

    public event Action? LayoutChanged;
    public event Action<VolumeStateMessage>? VolumeChanged;
    public event Action<MediaStateMessage>? MediaChanged;

    /// <summary>Un widget ha fallado al ejecutarse: qué widget y por qué.</summary>
    public event Action<string, Exception>? Failed;

    public WidgetService(IActionExecutor executor, IVolumeController volume, IMediaController media, TimeProvider? time = null)
    {
        _executor = executor;
        _volume = volume;
        _media = media;
        time ??= TimeProvider.System;
        _volumeOut = new RateLimiter<VolumeStateMessage>(time, MinStateInterval, m => VolumeChanged?.Invoke(m));
        _mediaOut = new RateLimiter<MediaStateMessage>(time, MinStateInterval, m => MediaChanged?.Invoke(m));
        _volume.Changed += OnVolumeChanged;
        _media.Changed += OnMediaChanged;
    }

    public void SetWidgets(IReadOnlyList<WidgetDefinition> widgets)
    {
        lock (_lock)
        {
            _widgets = widgets;
            _lastVolume = null;
            _lastMedia = null;
        }
        LayoutChanged?.Invoke();
    }

    public byte? VolumeWidgetId => IdOf<VolumeWidget>();

    public byte? MediaWidgetId => IdOf<MediaWidget>();

    private byte? IdOf<T>() where T : WidgetDefinition
    {
        lock (_lock)
        {
            for (var i = 0; i < _widgets.Count; i++)
                if (_widgets[i] is T) return (byte)i;
            return null;
        }
    }

    public WidgetLayoutMessage CurrentLayout()
    {
        lock (_lock)
        {
            var entries = _widgets
                .Take(ProtocolInfo.MaxWidgets)
                .Select((w, i) => w is ButtonWidget b
                    ? new WidgetLayoutEntry((byte)i, WidgetKind.Button, b.Label, b.Icon)
                    : new WidgetLayoutEntry((byte)i, w.Kind, "", ""))
                .ToList();
            return new WidgetLayoutMessage(entries);
        }
    }

    public VolumeStateMessage? CurrentVolume()
    {
        if (VolumeWidgetId is null) return null;
        var state = ReadVolume();
        lock (_lock) _lastVolume = state;
        return state;
    }

    public MediaStateMessage? CurrentMedia()
    {
        if (MediaWidgetId is null) return null;
        var state = ReadMedia();
        lock (_lock) _lastMedia = state;
        return state;
    }

    public GestureStatus Press(byte id)
    {
        ButtonWidget? button;
        lock (_lock) button = id < _widgets.Count ? _widgets[id] as ButtonWidget : null;
        if (button is null) return GestureStatus.Error;
        try
        {
            _executor.Execute(button.Action);
            return GestureStatus.Executed;
        }
        catch (Exception ex)
        {
            Failed?.Invoke(button.Label, ex);
            return GestureStatus.Error;
        }
    }

    public void SetVolume(int level) => Guard(CoreLoc.T("ws_volume"), () => _volume.SetLevel(Math.Clamp(level, 0, 100)));

    public void SetMuted(bool muted) => Guard(CoreLoc.T("ws_mute"), () => _volume.SetMuted(muted));

    public void SendMedia(MediaCommand command) => Guard(CoreLoc.T("ws_music"), () => _media.Send(command));

    private void Guard(string what, Action action)
    {
        try
        {
            action();
        }
        catch (Exception ex)
        {
            Failed?.Invoke(what, ex);
        }
    }

    private void OnVolumeChanged()
    {
        if (VolumeWidgetId is null) return;
        var state = ReadVolume();
        lock (_lock)
        {
            if (state == _lastVolume) return;
            _lastVolume = state;
        }
        _volumeOut.Post(state);
    }

    private void OnMediaChanged()
    {
        if (MediaWidgetId is null) return;
        var state = ReadMedia();
        lock (_lock)
        {
            if (state == _lastMedia) return;
            _lastMedia = state;
        }
        _mediaOut.Post(state);
    }

    private VolumeStateMessage ReadVolume()
    {
        var v = _volume.Get();
        return new VolumeStateMessage((byte)Math.Clamp(v.Level, 0, 100), v.Muted);
    }

    private MediaStateMessage ReadMedia()
    {
        var m = _media.Current();
        return new MediaStateMessage(m.Playing, m.Title ?? "", m.Artist ?? "");
    }

    public void Dispose()
    {
        _volume.Changed -= OnVolumeChanged;
        _media.Changed -= OnMediaChanged;
        _volumeOut.Dispose();
        _mediaOut.Dispose();
    }

    /// <summary>Deja pasar el primer valor al instante y, dentro del intervalo, solo el último (al terminar este).</summary>
    private sealed class RateLimiter<T>(TimeProvider time, TimeSpan interval, Action<T> publish) : IDisposable
    {
        private readonly object _gate = new();
        private long? _lastSent;
        private T? _pending;
        private bool _hasPending;
        private ITimer? _timer;
        private bool _disposed;

        public void Post(T value)
        {
            lock (_gate)
            {
                if (_disposed) return;
                var now = time.GetTimestamp();
                if (_lastSent is { } last && time.GetElapsedTime(last, now) is var elapsed && elapsed < interval)
                {
                    _pending = value;
                    _hasPending = true;
                    _timer ??= time.CreateTimer(_ => Flush(), null, interval - elapsed, Timeout.InfiniteTimeSpan);
                    return;
                }
                // Pasó el intervalo: se envía ya y se descarta lo que esperaba.
                _hasPending = false;
                _pending = default;
                _timer?.Dispose();
                _timer = null;
                _lastSent = now;
            }
            publish(value);
        }

        private void Flush()
        {
            T value;
            lock (_gate)
            {
                _timer?.Dispose();
                _timer = null;
                if (_disposed || !_hasPending) return;
                value = _pending!;
                _hasPending = false;
                _pending = default;
                _lastSent = time.GetTimestamp();
            }
            publish(value);
        }

        public void Dispose()
        {
            lock (_gate)
            {
                _disposed = true;
                _timer?.Dispose();
                _timer = null;
            }
        }
    }
}
