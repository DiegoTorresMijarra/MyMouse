using MyMouse.HostCore;
using MyMouse.Protocol;
using static MyMouse.Host.Native;

namespace MyMouse.Host;

/// <summary>
/// Inyección mediante Win32 SendInput.
/// Nota: por UIPI no afecta a ventanas que corren como administrador salvo que este proceso también lo esté.
/// Si falla (p. ej. escritorio seguro activo) se descarta el evento: no tiene sentido cortar la sesión.
/// </summary>
internal sealed class SendInputInjector : IInputInjector
{
    public void MoveRelative(int dx, int dy) => Send(Mouse(dx, dy, 0, MOUSEEVENTF_MOVE));

    public void SetButton(MouseButton button, bool pressed)
    {
        var flags = (button, pressed) switch
        {
            (MouseButton.Left, true) => MOUSEEVENTF_LEFTDOWN,
            (MouseButton.Left, false) => MOUSEEVENTF_LEFTUP,
            (MouseButton.Right, true) => MOUSEEVENTF_RIGHTDOWN,
            (MouseButton.Right, false) => MOUSEEVENTF_RIGHTUP,
            (MouseButton.Middle, true) => MOUSEEVENTF_MIDDLEDOWN,
            (MouseButton.Middle, false) => MOUSEEVENTF_MIDDLEUP,
            _ => throw new ArgumentOutOfRangeException(nameof(button)),
        };
        Send(Mouse(0, 0, 0, flags));
    }

    public void Scroll(int vertical, int horizontal)
    {
        if (vertical != 0) Send(Mouse(0, 0, vertical, MOUSEEVENTF_WHEEL));
        if (horizontal != 0) Send(Mouse(0, 0, horizontal, MOUSEEVENTF_HWHEEL));
    }

    public void Zoom(int delta)
    {
        if (delta == 0) return;
        // Ctrl pulsado + rueda + Ctrl soltado en una sola llamada, para que nada se cuele en medio.
        Send(Key(VK_CONTROL, up: false), Mouse(0, 0, delta, MOUSEEVENTF_WHEEL), Key(VK_CONTROL, up: true));
    }
}
