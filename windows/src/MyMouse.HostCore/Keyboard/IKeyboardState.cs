using MyMouse.Protocol;

namespace MyMouse.HostCore.Keyboard;

/// <summary>Lo que el PC sabe de su teclado y el móvil dibuja: luces y distribución. En Windows: GetKeyState y GetKeyboardLayout.</summary>
public interface IKeyboardState
{
    /// <summary>Luces actuales (<see cref="KeyboardLedsMessage.Caps"/>, <see cref="KeyboardLedsMessage.Num"/>, <see cref="KeyboardLedsMessage.Scroll"/>).</summary>
    byte ReadLeds();

    /// <summary>Id (<c>es</c> o <c>us</c>) de la distribución activa de la ventana en primer plano; vacío si no se sabe.</summary>
    string ReadLayoutId();
}

/// <summary>
/// Mientras el móvil está en modo teclado, vigila las luces y la distribución del PC y avisa al móvil: las dos nada más empezar y
/// después solo cuando cambian (docs/spec-v6-teclado.md §8). Se comprueba cada <see cref="DefaultInterval"/>.
/// </summary>
public sealed class KeyboardStateMonitor(IKeyboardState state, Func<Message, CancellationToken, ValueTask> send, TimeSpan? interval = null)
{
    public static readonly TimeSpan DefaultInterval = TimeSpan.FromMilliseconds(250);

    public async Task RunAsync(CancellationToken cancellationToken)
    {
        byte? leds = null;
        string? layout = null;
        using var timer = new PeriodicTimer(interval ?? DefaultInterval);
        try
        {
            do
            {
                var nowLeds = state.ReadLeds();
                if (leds != nowLeds)
                {
                    leds = nowLeds;
                    await send(new KeyboardLedsMessage(nowLeds), cancellationToken).ConfigureAwait(false);
                }

                var nowLayout = state.ReadLayoutId();
                if (nowLayout.Length > 0 && layout != nowLayout)
                {
                    layout = nowLayout;
                    await send(new KeyboardLayoutMessage(nowLayout), cancellationToken).ConfigureAwait(false);
                }
            }
            while (await timer.WaitForNextTickAsync(cancellationToken).ConfigureAwait(false));
        }
        catch (Exception ex) when (ex is OperationCanceledException or IOException or ObjectDisposedException)
        {
            // El modo teclado terminó o la sesión se está cerrando.
        }
    }
}
