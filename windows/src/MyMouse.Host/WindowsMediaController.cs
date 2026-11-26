using MyMouse.HostCore.Gestures;
using MyMouse.HostCore.Widgets;
using MyMouse.Protocol;
using Windows.Media.Control;

namespace MyMouse.Host;

/// <summary>
/// Música del sistema: título, artista y estado con las sesiones multimedia de Windows (WinRT) y las órdenes como
/// teclas multimedia, que funcionan aunque la API de sesiones falle (spec §5). Si la API no está disponible, se apunta
/// una vez en el log y el estado queda en "nada en reproducción".
/// </summary>
internal sealed class WindowsMediaController : IMediaController, IDisposable
{
    private readonly IActionExecutor _executor;
    private readonly Action<string> _log;
    private readonly SemaphoreSlim _refreshGate = new(1, 1);
    private readonly object _lock = new();
    private GlobalSystemMediaTransportControlsSessionManager? _manager;
    private GlobalSystemMediaTransportControlsSession? _session;
    private MediaInfo _current = MediaInfo.Nothing;
    private byte[]? _thumbnail;
    private bool _failureLogged;
    private bool _disposed;

    public event Action? Changed;

    public WindowsMediaController(IActionExecutor executor, Action<string> log)
    {
        _executor = executor;
        _log = log;
        _ = InitAsync();
    }

    public MediaInfo Current()
    {
        lock (_lock) return _current;
    }

    /// <summary>Carátula de lo que suena (imagen codificada: JPEG o PNG), o null si no hay. La misma instancia mientras no cambie.</summary>
    public byte[]? CurrentThumbnail
    {
        get
        {
            lock (_lock) return _thumbnail;
        }
    }

    /// <summary>Lanza si Windows rechaza las teclas (el servicio lo informa como error al móvil).</summary>
    public void Send(MediaCommand command)
    {
        var key = command switch
        {
            MediaCommand.PlayPause => "PlayPause",
            MediaCommand.Next => "Next",
            MediaCommand.Previous => "Previous",
            _ => throw new ArgumentOutOfRangeException(nameof(command)),
        };
        _executor.Execute(new MediaAction(key, KeyChord.Parse(key)[0]));
    }

    private async Task InitAsync()
    {
        try
        {
            var manager = await GlobalSystemMediaTransportControlsSessionManager.RequestAsync();
            lock (_lock)
            {
                if (_disposed) return;
                _manager = manager;
            }
            manager.CurrentSessionChanged += OnCurrentSessionChanged;
            AttachSession();
        }
        catch (Exception ex)
        {
            LogOnce(ex);
        }
    }

    private void OnCurrentSessionChanged(GlobalSystemMediaTransportControlsSessionManager sender, CurrentSessionChangedEventArgs args) =>
        AttachSession();

    private void OnMediaPropertiesChanged(GlobalSystemMediaTransportControlsSession sender, MediaPropertiesChangedEventArgs args) =>
        _ = RefreshAsync();

    private void OnPlaybackInfoChanged(GlobalSystemMediaTransportControlsSession sender, PlaybackInfoChangedEventArgs args) =>
        _ = RefreshAsync();

    /// <summary>Cambia de sesión (otra aplicación de música pasa a ser la actual) y relee el estado.</summary>
    private void AttachSession()
    {
        try
        {
            lock (_lock)
            {
                if (_disposed) return;
                if (_session is not null)
                {
                    _session.MediaPropertiesChanged -= OnMediaPropertiesChanged;
                    _session.PlaybackInfoChanged -= OnPlaybackInfoChanged;
                }
                _session = _manager?.GetCurrentSession();
                if (_session is not null)
                {
                    _session.MediaPropertiesChanged += OnMediaPropertiesChanged;
                    _session.PlaybackInfoChanged += OnPlaybackInfoChanged;
                }
            }
        }
        catch (Exception ex)
        {
            LogOnce(ex);
        }
        _ = RefreshAsync();
    }

    private async Task RefreshAsync()
    {
        await _refreshGate.WaitAsync();
        try
        {
            GlobalSystemMediaTransportControlsSession? session;
            lock (_lock) session = _session;

            var info = MediaInfo.Nothing;
            byte[]? thumbnail = null;
            if (session is not null)
            {
                var properties = await session.TryGetMediaPropertiesAsync();
                var status = session.GetPlaybackInfo()?.PlaybackStatus;
                info = new MediaInfo(
                    status == GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing,
                    properties?.Title ?? "",
                    properties?.Artist ?? "");
                // La carátula solo se vuelve a leer si cambia la canción.
                bool sameSong;
                lock (_lock) sameSong = info.Title == _current.Title && info.Artist == _current.Artist;
                thumbnail = sameSong ? CurrentThumbnail : await ReadThumbnailAsync(properties);
            }

            bool changed;
            lock (_lock)
            {
                if (_disposed) return;
                changed = info != _current || !ReferenceEquals(thumbnail, _thumbnail);
                _current = info;
                _thumbnail = thumbnail;
            }
            if (changed) Changed?.Invoke();
        }
        catch (Exception ex)
        {
            LogOnce(ex);
        }
        finally
        {
            _refreshGate.Release();
        }
    }

    private async Task<byte[]?> ReadThumbnailAsync(GlobalSystemMediaTransportControlsSessionMediaProperties? properties)
    {
        try
        {
            if (properties?.Thumbnail is not { } reference) return null;
            using var stream = (await reference.OpenReadAsync()).AsStreamForRead();
            using var copy = new MemoryStream();
            await stream.CopyToAsync(copy);
            return copy.Length is > 0 and < 8_000_000 ? copy.ToArray() : null;
        }
        catch (Exception ex)
        {
            // La carátula es un adorno: si no se puede leer, se muestra sin ella.
            LogOnce(ex);
            return null;
        }
    }

    private void LogOnce(Exception ex)
    {
        if (_failureLogged) return;
        _failureLogged = true;
        _log($"Música no disponible (las teclas multimedia siguen funcionando): {ex.Message}");
    }

    public void Dispose()
    {
        lock (_lock)
        {
            _disposed = true;
            try
            {
                if (_session is not null)
                {
                    _session.MediaPropertiesChanged -= OnMediaPropertiesChanged;
                    _session.PlaybackInfoChanged -= OnPlaybackInfoChanged;
                }
                if (_manager is not null) _manager.CurrentSessionChanged -= OnCurrentSessionChanged;
            }
            catch (Exception)
            {
                // Al salir no importa.
            }
        }
    }
}
