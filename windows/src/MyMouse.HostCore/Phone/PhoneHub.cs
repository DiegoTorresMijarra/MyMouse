using MyMouse.Protocol;

namespace MyMouse.HostCore.Phone;

/// <summary>
/// Punto de unión entre la sesión del móvil (<see cref="ClientSession"/>) y el resto del PC: recibe los
/// mensajes <c>TOOLBOX</c>, <c>GESTURE_MODE</c>, <c>STROKE_*</c>, <c>SHAPE_*</c> y <c>THEME</c> del móvil, sabe si está
/// en modo teclado y permite enviarle mensajes (<c>SHAPE_ADD</c>, <c>SHAPE_DELETE</c>, <c>KEYBOARD_OPEN</c>, <c>THEME</c>). Los eventos se disparan en el hilo de la sesión: quien los recibe debe
/// pasar al hilo de la interfaz si hace falta.
/// </summary>
public sealed class PhoneHub
{
    private readonly object _lock = new();
    private Attachment? _current;

    /// <summary>Versión negociada con el móvil conectado; 0 si no hay móvil.</summary>
    public byte Version
    {
        get { lock (_lock) return _current?.Version ?? 0; }
    }

    /// <summary>¿Hay un móvil conectado por USB?</summary>
    public bool Connected => Version > 0;

    /// <summary>Versión de la app del móvil conectado (<c>APP_VERSION</c>), o null si no hay móvil o aún no la ha dicho.</summary>
    public string? PhoneAppVersion
    {
        get { lock (_lock) return _current?.AppVersion; }
    }

    /// <summary>Versión de MyMouse de este PC, que se envía al móvil tras el handshake. Vacía = no se envía.</summary>
    public string HostAppVersion { get; set; } = "";

    /// <summary>Tema de este PC, que se envía al móvil tras el handshake. Null = no se envía. Lo pone <c>ThemeSyncService</c>.</summary>
    public Func<ThemeMessage>? LocalTheme { get; set; }

    /// <summary>¿Está el móvil conectado en modo teclado (<c>KEYBOARD_MODE</c>)?</summary>
    public bool KeyboardMode
    {
        get { lock (_lock) return _current?.KeyboardMode ?? false; }
    }

    /// <summary>El móvil entra (true) o sale (false) del modo teclado. Se dispara en el hilo de la sesión.</summary>
    public event Action<bool>? KeyboardModeChanged;

    /// <summary>Se conecta o desconecta un móvil.</summary>
    public event Action? Changed;

    /// <summary>Mensaje de la toolbox o del tema recibido del móvil.</summary>
    public event Action<Message>? Received;

    /// <summary>Lo llama <see cref="ClientSession"/> tras el handshake con versión ≥ 5. Desechar el resultado al terminar la sesión.</summary>
    public IDisposable Attach(byte version, Func<Message, CancellationToken, ValueTask> send)
    {
        var attachment = new Attachment(this, version, send);
        lock (_lock) _current = attachment;
        Changed?.Invoke();
        return attachment;
    }

    /// <summary>Lo llama <see cref="ClientSession"/> al recibir <c>APP_VERSION</c> del móvil.</summary>
    public void SetPhoneAppVersion(string version)
    {
        lock (_lock)
        {
            if (_current is null) return;
            _current.AppVersion = version;
        }
        Changed?.Invoke();
    }

    /// <summary>Lo llama <see cref="ClientSession"/> al recibir <c>KEYBOARD_MODE</c>. También avisa por <see cref="Changed"/>.</summary>
    public void SetKeyboardMode(bool on)
    {
        lock (_lock)
        {
            if (_current is null || _current.KeyboardMode == on) return;
            _current.KeyboardMode = on;
        }
        KeyboardModeChanged?.Invoke(on);
        Changed?.Invoke();
    }

    /// <summary>Lo llama <see cref="ClientSession"/> con cada mensaje de la toolbox o del tema que llega.</summary>
    public void Dispatch(Message message) => Received?.Invoke(message);

    /// <summary>Envía un mensaje al móvil. <c>false</c> si no hay móvil conectado o el envío falla.</summary>
    public async Task<bool> SendAsync(Message message, CancellationToken cancellationToken = default)
    {
        Attachment? attachment;
        lock (_lock) attachment = _current;
        if (attachment is null) return false;
        try
        {
            await attachment.Send(message, cancellationToken).ConfigureAwait(false);
            return true;
        }
        catch (Exception ex) when (ex is IOException or OperationCanceledException or ObjectDisposedException)
        {
            return false;
        }
    }

    private void Detach(Attachment attachment)
    {
        lock (_lock)
        {
            // Una sesión nueva puede haber sustituido ya a la anterior.
            if (!ReferenceEquals(_current, attachment)) return;
            _current = null;
        }
        Changed?.Invoke();
    }

    private sealed class Attachment(PhoneHub hub, byte version, Func<Message, CancellationToken, ValueTask> send) : IDisposable
    {
        public byte Version { get; } = version;

        public string? AppVersion { get; set; }

        public bool KeyboardMode { get; set; }

        public ValueTask Send(Message message, CancellationToken cancellationToken) => send(message, cancellationToken);

        public void Dispose() => hub.Detach(this);
    }
}
