using MyMouse.Protocol;

namespace MyMouse.HostCore;

/// <summary>Inyecta entrada en el sistema operativo. En Windows se implementa con SendInput.</summary>
public interface IInputInjector
{
    void MoveRelative(int dx, int dy);
    void SetButton(MouseButton button, bool pressed);

    /// <summary>Unidades de rueda (120 = un paso). Vertical positivo = arriba; horizontal positivo = derecha.</summary>
    void Scroll(int vertical, int horizontal);

    /// <summary>Zoom como Ctrl + rueda vertical (120 = un paso; positivo = acercar).</summary>
    void Zoom(int delta);

    /// <summary>
    /// Gesto de 3 dedos de Windows: Win+Tab, Win+D o cambio de app. <see cref="SystemGestureCommand.AppSwitchStart"/> deja Alt
    /// pulsado hasta <see cref="SystemGestureCommand.AppSwitchEnd"/>; <see cref="ClientSession"/> garantiza que se envíe el final.
    /// </summary>
    void SystemGesture(SystemGestureCommand command);

    /// <summary>
    /// Pulsa o suelta una tecla del modo teclado por su uso HID (página 0x07) con su scancode (docs/spec-v6-teclado.md §2).
    /// <see cref="ClientSession"/> solo llama con usos que existen en <see cref="Keyboard.HidScancodes"/> y suelta las teclas que sigan pulsadas.
    /// </summary>
    void SetKey(byte usage, bool down);
}
