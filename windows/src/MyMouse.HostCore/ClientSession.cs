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
    private readonly HashSet<MouseButton> _pressed = [];

    public ClientSession(Stream input, Stream output, IInputInjector injector,
        Func<bool>? isPaused = null, TimeSpan? idleTimeout = null)
    {
        _input = input;
        _output = output;
        _injector = injector;
        _isPaused = isPaused ?? (() => false);
        _idleTimeout = idleTimeout ?? ProtocolInfo.IdleTimeout;
    }

    public ClientSession(Stream duplex, IInputInjector injector, Func<bool>? isPaused = null)
        : this(duplex, duplex, injector, isPaused) { }

    /// <summary>Último error de protocolo, si la sesión terminó por uno.</summary>
    public string? Error { get; private set; }

    public async Task<SessionEndReason> RunAsync(CancellationToken cancellationToken)
    {
        var reader = new MessageReader(_input);
        var handshakeDone = false;

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
                    if (hello.Version != ProtocolInfo.CurrentVersion)
                        return Fail($"Versión no soportada: {hello.Version}");

                    await SendAsync(new HelloAckMessage(ProtocolInfo.CurrentVersion), cancellationToken).ConfigureAwait(false);
                    handshakeDone = true;
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
            ReleaseAll();
        }
    }

    private async ValueTask HandleAsync(Message message, CancellationToken cancellationToken)
    {
        if (message is PingMessage)
        {
            await SendAsync(PongMessage.Instance, cancellationToken).ConfigureAwait(false);
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
            case HelloMessage:
                throw new ProtocolException("HELLO duplicado");
            default:
                throw new ProtocolException($"Mensaje no válido del cliente: {message.Type}");
        }
    }

    private async ValueTask SendAsync(Message message, CancellationToken cancellationToken)
    {
        await _output.WriteAsync(MessageCodec.Encode(message), cancellationToken).ConfigureAwait(false);
        await _output.FlushAsync(cancellationToken).ConfigureAwait(false);
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
