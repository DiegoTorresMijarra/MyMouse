using MyMouse.HostCore.Phone;
using MyMouse.HostCore.Widgets;
using MyMouse.Protocol;

namespace MyMouse.HostCore;

public enum SessionEndReason
{
    ClientClosed,
    Timeout,
    ProtocolError,
    Cancelled,
    IoError,
}

/// <summary>
/// Atiende a un cliente conectado: handshake, mensajes de entrada y keepalive.
/// Al terminar, suelta cualquier botón que siga pulsado.
/// </summary>
public sealed class ClientSession
{
    private readonly Stream _input;
    private readonly Stream _output;
    private readonly IInputInjector _injector;
    private readonly Func<bool> _isPaused;
    private readonly TimeSpan _idleTimeout;
    private readonly IGestureService? _gestures;
    private readonly IWidgetService? _widgets;
    private readonly PhoneHub? _phone;
    private readonly HashSet<MouseButton> _pressed = [];

    /// <summary>Cambio de app en curso: el inyector tiene Alt pulsado y hay que soltarlo si la sesión se corta.</summary>
    private bool _appSwitching;

    /// <summary>Las escrituras pueden venir del bucle de lectura y de los avisos de la lista de formas y de los widgets.</summary>
    private readonly SemaphoreSlim _writeLock = new(1, 1);

    public ClientSession(Stream input, Stream output, IInputInjector injector,
        Func<bool>? isPaused = null, TimeSpan? idleTimeout = null, IGestureService? gestures = null,
        IWidgetService? widgets = null, PhoneHub? phone = null)
    {
        _input = input;
        _output = output;
        _injector = injector;
        _isPaused = isPaused ?? (() => false);
        _idleTimeout = idleTimeout ?? ProtocolInfo.IdleTimeout;
        _gestures = gestures;
        _widgets = widgets;
        _phone = phone;
    }

    public ClientSession(Stream duplex, IInputInjector injector, Func<bool>? isPaused = null,
        IGestureService? gestures = null, IWidgetService? widgets = null, PhoneHub? phone = null)
        : this(duplex, duplex, injector, isPaused, gestures: gestures, widgets: widgets, phone: phone) { }

    /// <summary>Último error de protocolo, si la sesión terminó por uno.</summary>
    public string? Error { get; private set; }

    /// <summary>Versión acordada en el handshake (0 hasta entonces).</summary>
    public byte Version { get; private set; }

    public async Task<SessionEndReason> RunAsync(CancellationToken cancellationToken)
    {
        var reader = new MessageReader(_input);
        var handshakeDone = false;
        Action? onListChanged = null;
        Action? onLayoutChanged = null;
        Action<VolumeStateMessage>? onVolume = null;
        Action<MediaStateMessage>? onMedia = null;
        IDisposable? phoneAttachment = null;
        using var sessionCts = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);

        try
        {
            while (true)
            {
                Message? message;
                using (var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken))
                {
                    timeout.CancelAfter(_idleTimeout);
                    try
                    {
                        message = await reader.ReadAsync(timeout.Token).ConfigureAwait(false);
                    }
                    catch (OperationCanceledException) when (!cancellationToken.IsCancellationRequested)
                    {
                        return SessionEndReason.Timeout;
                    }
                }

                if (message is null)
                    return SessionEndReason.ClientClosed;

                if (!handshakeDone)
                {
                    if (message is not HelloMessage hello)
                        return Fail($"Se esperaba HELLO, llegó {message.Type}");
                    if (hello.Version == 0)
                        return Fail("Versión no soportada: 0");

                    Version = Math.Min(hello.Version, ProtocolInfo.CurrentVersion);
                    await SendAsync(new HelloAckMessage(Version), cancellationToken).ConfigureAwait(false);
                    handshakeDone = true;

                    if (_gestures is not null && Version >= ProtocolInfo.GesturesVersion)
                    {
                        await SendAsync(_gestures.CurrentList(), cancellationToken).ConfigureAwait(false);
                        var token = sessionCts.Token;
                        onListChanged = () => _ = SendListSafelyAsync(token);
                        _gestures.ListChanged += onListChanged;
                    }

                    if (_widgets is not null && Version >= ProtocolInfo.WidgetsVersion)
                    {
                        // Se suscribe antes de enviar el estado inicial para no perder cambios: un aviso repetido es inocuo.
                        var token = sessionCts.Token;
                        onLayoutChanged = () => _ = SendSafelyAsync(SendWidgetsAsync, token);
                        onVolume = state => _ = SendSafelyAsync(ct => SendAsync(state, ct).AsTask(), token);
                        onMedia = state => _ = SendSafelyAsync(ct => SendAsync(state, ct).AsTask(), token);
                        _widgets.LayoutChanged += onLayoutChanged;
                        _widgets.VolumeChanged += onVolume;
                        _widgets.MediaChanged += onMedia;
                        await SendWidgetsAsync(cancellationToken).ConfigureAwait(false);
                    }

                    // La toolbox (v5) recibe lo que llega del móvil y puede escribirle (SHAPE_ADD, SHAPE_DELETE).
                    if (_phone is not null && Version >= ProtocolInfo.ToolboxVersion)
                        phoneAttachment = _phone.Attach(Version, SendAsync);
                    continue;
                }

                await HandleAsync(message, cancellationToken).ConfigureAwait(false);
            }
        }
        catch (ProtocolException ex)
        {
            return Fail(ex.Message);
        }
        catch (OperationCanceledException)
        {
            return SessionEndReason.Cancelled;
        }
        catch (IOException ex)
        {
            Error = ex.Message;
            return SessionEndReason.IoError;
        }
        finally
        {
            if (onListChanged is not null) _gestures!.ListChanged -= onListChanged;
            if (onLayoutChanged is not null) _widgets!.LayoutChanged -= onLayoutChanged;
            if (onVolume is not null) _widgets!.VolumeChanged -= onVolume;
            if (onMedia is not null) _widgets!.MediaChanged -= onMedia;
            phoneAttachment?.Dispose();
            sessionCts.Cancel();
            ReleaseAll();
        }
    }

    private async Task SendListSafelyAsync(CancellationToken cancellationToken)
    {
        try
        {
            await SendAsync(_gestures!.CurrentList(), cancellationToken).ConfigureAwait(false);
        }
        catch (Exception ex) when (ex is IOException or OperationCanceledException or ObjectDisposedException)
        {
            // La sesión se está cerrando: el bucle principal ya lo gestiona.
        }
    }

    /// <summary>Layout y, si hay widgets de volumen o música, su estado actual.</summary>
    private async Task SendWidgetsAsync(CancellationToken cancellationToken)
    {
        await SendAsync(_widgets!.CurrentLayout(), cancellationToken).ConfigureAwait(false);
        if (_widgets.CurrentVolume() is { } volume)
            await SendAsync(volume, cancellationToken).ConfigureAwait(false);
        if (_widgets.CurrentMedia() is { } media)
            await SendAsync(media, cancellationToken).ConfigureAwait(false);
    }

    private static async Task SendSafelyAsync(Func<CancellationToken, Task> send, CancellationToken cancellationToken)
    {
        try
        {
            await send(cancellationToken).ConfigureAwait(false);
        }
        catch (Exception ex) when (ex is IOException or OperationCanceledException or ObjectDisposedException)
        {
            // La sesión se está cerrando: el bucle principal ya lo gestiona.
        }
    }

    private async ValueTask HandleAsync(Message message, CancellationToken cancellationToken)
    {
        if (message is PingMessage)
        {
            await SendAsync(PongMessage.Instance, cancellationToken).ConfigureAwait(false);
            return;
        }

        if (message is GestureMessage gesture)
        {
            if (Version < ProtocolInfo.GesturesVersion)
                throw new ProtocolException("GESTURE con protocolo v1");
            var status = _isPaused() || _gestures is null ? GestureStatus.Error : _gestures.Execute(gesture.Name);
            await SendAsync(new GestureResultMessage(status), cancellationToken).ConfigureAwait(false);
            return;
        }

        if (message is ToolboxMessage or GestureModeMessage or StrokePointsMessage or StrokeEndMessage
            or ShapeInfoMessage or ShapeListEndMessage or ShapeResultMessage)
        {
            // Entran en la versión 5. Se atienden aunque el PC esté en pausa: la toolbox sigue siendo útil.
            if (Version < ProtocolInfo.ToolboxVersion)
                throw new ProtocolException($"{message.Type} con protocolo v{Version}");
            _phone?.Dispatch(message);
            return;
        }

        if (message is WidgetPressMessage or VolumeSetMessage or MediaCommandMessage or VolumeMuteMessage)
        {
            await HandleWidgetAsync(message, cancellationToken).ConfigureAwait(false);
            return;
        }

        if (message is SystemGestureMessage && Version < ProtocolInfo.SystemGestureVersion)
            throw new ProtocolException($"SYSTEM_GESTURE con protocolo v{Version}");

        if (_isPaused())
        {
            ReleaseAll();
            return;
        }

        switch (message)
        {
            case MoveMessage m:
                _injector.MoveRelative(m.Dx, m.Dy);
                break;
            case ButtonMessage b:
                if (b.Pressed ? _pressed.Add(b.Button) : _pressed.Remove(b.Button))
                    _injector.SetButton(b.Button, b.Pressed);
                break;
            case ScrollMessage s:
                _injector.Scroll(s.Vertical, s.Horizontal);
                break;
            case ZoomMessage z:
                _injector.Zoom(z.Delta);
                break;
            case SystemGestureMessage g:
                HandleSystemGesture(g.Command);
                break;
            case HelloMessage:
                throw new ProtocolException("HELLO duplicado");
            default:
                throw new ProtocolException($"Mensaje no válido del cliente: {message.Type}");
        }
    }

    private async ValueTask HandleWidgetAsync(Message message, CancellationToken cancellationToken)
    {
        if (Version < ProtocolInfo.WidgetsVersion)
            throw new ProtocolException($"{message.Type} con protocolo v{Version}");

        var paused = _isPaused();
        if (message is WidgetPressMessage press)
        {
            var status = paused || _widgets is null ? GestureStatus.Error : _widgets.Press(press.Id);
            await SendAsync(new WidgetResultMessage(press.Id, status), cancellationToken).ConfigureAwait(false);
            return;
        }

        // Volumen y música no se responden si salen bien; sin el widget correspondiente se ignoran.
        var widgetId = message is MediaCommandMessage ? _widgets?.MediaWidgetId : _widgets?.VolumeWidgetId;
        if (widgetId is not { } id) return;
        if (paused)
        {
            await SendAsync(new WidgetResultMessage(id, GestureStatus.Error), cancellationToken).ConfigureAwait(false);
            return;
        }

        switch (message)
        {
            case VolumeSetMessage v:
                _widgets!.SetVolume(Math.Min(v.Level, (byte)100));
                break;
            case VolumeMuteMessage m:
                _widgets!.SetMuted(m.Muted);
                break;
            case MediaCommandMessage c:
                _widgets!.SendMedia(c.Command);
                break;
        }
    }

    private async ValueTask SendAsync(Message message, CancellationToken cancellationToken)
    {
        var bytes = MessageCodec.Encode(message);
        await _writeLock.WaitAsync(cancellationToken).ConfigureAwait(false);
        try
        {
            await _output.WriteAsync(bytes, cancellationToken).ConfigureAwait(false);
            await _output.FlushAsync(cancellationToken).ConfigureAwait(false);
        }
        finally
        {
            _writeLock.Release();
        }
    }

    private void HandleSystemGesture(SystemGestureCommand command)
    {
        switch (command)
        {
            case SystemGestureCommand.AppSwitchStart:
                if (_appSwitching) return; // Alt ya está pulsado
                _appSwitching = true;
                break;
            case SystemGestureCommand.AppSwitchNext or SystemGestureCommand.AppSwitchPrevious:
                if (!_appSwitching) return; // sin START no hay Alt pulsado: se ignora
                break;
            case SystemGestureCommand.AppSwitchEnd:
                if (!_appSwitching) return;
                _appSwitching = false;
                break;
        }
        _injector.SystemGesture(command);
    }

    private void ReleaseAll()
    {
        foreach (var button in _pressed)
            _injector.SetButton(button, false);
        _pressed.Clear();

        // Un cambio de app a medias (sesión cortada o PC en pausa) no puede dejar Alt pulsado.
        if (_appSwitching)
        {
            _appSwitching = false;
            _injector.SystemGesture(SystemGestureCommand.AppSwitchEnd);
        }
    }

    private SessionEndReason Fail(string error)
    {
        Error = error;
        return SessionEndReason.ProtocolError;
    }
}
