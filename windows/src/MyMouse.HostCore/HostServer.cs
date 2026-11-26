using System.Net;
using System.Net.Sockets;
using MyMouse.HostCore.Phone;
using MyMouse.HostCore.Widgets;
using MyMouse.Protocol;

namespace MyMouse.HostCore;

/// <summary>
/// Servidor TCP en loopback que atiende a un único cliente a la vez.
/// Una conexión nueva sustituye a la anterior.
/// </summary>
public sealed class HostServer(
    IInputInjector injector, Func<bool> isPaused, int port = ProtocolInfo.DefaultPort, IGestureService? gestures = null,
    IWidgetService? widgets = null, PhoneHub? phone = null)
{
    private CancellationTokenSource? _currentSession;

    public event Action<bool>? ConnectionChanged;
    public event Action<string>? Log;

    public async Task RunAsync(CancellationToken cancellationToken)
    {
        var listener = new TcpListener(IPAddress.Loopback, port);
        listener.Start();
        Log?.Invoke($"Escuchando en 127.0.0.1:{port}");

        try
        {
            while (!cancellationToken.IsCancellationRequested)
            {
                var client = await listener.AcceptTcpClientAsync(cancellationToken).ConfigureAwait(false);
                client.NoDelay = true;

                // Sustituye la sesión anterior, si la hay.
                var sessionCts = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
                var previous = Interlocked.Exchange(ref _currentSession, sessionCts);
                try { previous?.Cancel(); } catch (ObjectDisposedException) { }

                _ = ServeAsync(client, sessionCts);
            }
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
        }
        finally
        {
            listener.Stop();
        }
    }

    private async Task ServeAsync(TcpClient client, CancellationTokenSource sessionCts)
    {
        using (client)
        {
            Log?.Invoke($"Cliente conectado desde {client.Client.RemoteEndPoint}");
            ConnectionChanged?.Invoke(true);

            try
            {
                var session = new ClientSession(client.GetStream(), injector, isPaused, gestures: gestures, widgets: widgets, phone: phone);
                var reason = await session.RunAsync(sessionCts.Token).ConfigureAwait(false);
                Log?.Invoke($"Cliente desconectado (protocolo v{session.Version}): {reason}{(session.Error is null ? "" : $" ({session.Error})")}");
            }
            catch (Exception ex)
            {
                Log?.Invoke($"Error inesperado en la sesión: {ex}");
            }

            if (Interlocked.CompareExchange(ref _currentSession, null, sessionCts) == sessionCts)
                ConnectionChanged?.Invoke(false);
            sessionCts.Dispose();
        }
    }
}
