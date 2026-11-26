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
}
