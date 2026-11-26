using MyMouse.Protocol;

namespace MyMouse.HostCore;

/// <summary>Ejecuta las acciones asociadas a las formas del modo gestos y publica la lista para la chuleta.</summary>
public interface IGestureService
{
    /// <summary>Ejecuta la acción de la forma <paramref name="name"/> según la configuración y la ventana activa.</summary>
    GestureStatus Execute(string name);

    /// <summary>Lista actual de formas con acción (depende de la ventana activa si hay reglas por aplicación).</summary>
    GestureListMessage CurrentList();

    /// <summary>La lista ha cambiado (configuración recargada o cambio de ventana activa).</summary>
    event Action? ListChanged;
}
