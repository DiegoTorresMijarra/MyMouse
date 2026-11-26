using MyMouse.HostCore.Widgets;
using NAudio.CoreAudioApi;

namespace MyMouse.Host;

/// <summary>
/// Volumen del altavoz por defecto con Core Audio (<c>IAudioEndpointVolume</c> vía NAudio). Si el usuario cambia de
/// dispositivo por defecto, se vuelve a enlazar. Si algo falla se apunta una vez en el log y el estado queda en 0.
/// </summary>
internal sealed class WasapiVolumeController : IVolumeController, IDisposable
{
    private readonly object _lock = new();
    private readonly Action<string> _log;
    private MMDeviceEnumerator? _enumerator;
    private MMDeviceNotificationClient? _notifications;
    private MMDevice? _device;
    private AudioEndpointVolume? _endpoint;
    private bool _failureLogged;
    private bool _disposed;

    public event Action? Changed;

    public WasapiVolumeController(Action<string> log)
    {
        _log = log;
        try
        {
            _enumerator = new MMDeviceEnumerator();
            // Sin contexto de sincronización: los avisos llegan en el hilo de audio de Windows.
            _notifications = _enumerator.CreateNotificationClient(useSynchronizationContext: false);
            // Desde ese hilo no se puede volver a llamar a la pila de audio: se enlaza en otro.
            _notifications.DefaultDeviceChanged += (_, _) => ThreadPool.QueueUserWorkItem(_ => Bind());
        }
        catch (Exception ex)
        {
            LogOnce(ex);
        }
        Bind();
    }

    private void Bind()
    {
        lock (_lock)
        {
            if (_disposed || _enumerator is null) return;
            Unbind();
            try
            {
                if (!_enumerator.TryGetDefaultAudioEndpoint(DataFlow.Render, Role.Multimedia, out var device) || device is null)
                    return;
                _device = device;
                _endpoint = device.AudioEndpointVolume;
                _endpoint.OnVolumeNotification += OnNotification;
            }
            catch (Exception ex)
            {
                LogOnce(ex);
                Unbind();
            }
        }
        // El dispositivo nuevo puede tener otro volumen.
        Changed?.Invoke();
    }

    private void Unbind()
    {
        if (_endpoint is not null)
        {
            try { _endpoint.OnVolumeNotification -= OnNotification; } catch (Exception) { /* dispositivo ya desaparecido */ }
            try { _endpoint.Dispose(); } catch (Exception) { }
        }
        try { _device?.Dispose(); } catch (Exception) { }
        _endpoint = null;
        _device = null;
    }

    /// <summary>Llega en el hilo de audio de Windows: se avisa desde otro para poder leer el estado con calma.</summary>
    private void OnNotification(AudioVolumeNotificationData data) =>
        ThreadPool.QueueUserWorkItem(_ => Changed?.Invoke());

    public VolumeInfo Get()
    {
        lock (_lock)
        {
            if (_endpoint is null) return new VolumeInfo(0, false);
            try
            {
                return new VolumeInfo((int)Math.Round(_endpoint.MasterVolumeLevelScalar * 100), _endpoint.Mute);
            }
            catch (Exception ex)
            {
                LogOnce(ex);
                return new VolumeInfo(0, false);
            }
        }
    }

    public void SetLevel(int level)
    {
        lock (_lock)
        {
            if (_endpoint is null) return;
            try
            {
                _endpoint.MasterVolumeLevelScalar = Math.Clamp(level, 0, 100) / 100f;
            }
            catch (Exception ex)
            {
                LogOnce(ex);
            }
        }
    }

    public void SetMuted(bool muted)
    {
        lock (_lock)
        {
            if (_endpoint is null) return;
            try
            {
                _endpoint.Mute = muted;
            }
            catch (Exception ex)
            {
                LogOnce(ex);
            }
        }
    }

    private void LogOnce(Exception ex)
    {
        if (_failureLogged) return;
        _failureLogged = true;
        _log($"Volumen no disponible (se reintenta en silencio): {ex.Message}");
    }

    public void Dispose()
    {
        lock (_lock)
        {
            _disposed = true;
            Unbind();
        }
        try { _notifications?.Dispose(); } catch (Exception) { }
        try { _enumerator?.Dispose(); } catch (Exception) { }
    }
}
