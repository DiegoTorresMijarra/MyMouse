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
    private readonly HashSet<MouseButton> _pressed = [];

    /// <summary>Las escrituras pueden venir del bucle de lectura y de los avisos de la lista de formas.</summary>
    private readonly SemaphoreSlim _writeLock = new(1, 1);

    public ClientSession(Stream input, Stream output, IInputInjector injector,
        Func<bool>? isPaused = null, TimeSpan? idleTimeout = null, IGestureService? gestures = null)
    {
        _input = input;
        _output = output;
        _injector = injector;
        _isPaused = isPaused ?? (() => false);
        _idleTimeout = idleTimeout ?? ProtocolInfo.IdleTimeout;
        _gestures = gestures;
    }

    public ClientSession(Stream duplex, IInputInjector injector, Func<bool>? isPaused = null, IGestureService? gestures = null)
        : this(duplex, duplex, injector, isPaused, gestures: gestures) { }

    /// <summary>Último error de protocolo, si la sesión terminó por uno.</summary>
    public string? Error { get; private set; }

    /// <summary>Versión acordada en el handshake (0 hasta entonces).</summary>
    public byte Version { get; private set; }

    public async Task<SessionEndReason> RunAsync(CancellationToken cancellationToken)
    {
        var reader = new MessageReader(_input);
        var handshakeDone = false;
        Action? onListChanged = null;
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
            case HelloMessage:
                throw new ProtocolException("HELLO duplicado");
            default:
                throw new ProtocolException($"Mensaje no válido del cliente: {message.Type}");
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

    private void ReleaseAll()
    {
        foreach (var button in _pressed)
            _injector.SetButton(button, false);
        _pressed.Clear();
    }

    private SessionEndReason Fail(string error)
    {
        Error = error;
        return SessionEndReason.ProtocolError;
    }
}
