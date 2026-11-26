using MyMouse.Protocol;

namespace MyMouse.HostCore.Widgets;

/// <summary>Volumen del altavoz por defecto (0–100) y si está silenciado.</summary>
public sealed record VolumeInfo(int Level, bool Muted);

/// <summary>Lo que suena en el PC. Título y artista vacíos si no suena nada.</summary>
public sealed record MediaInfo(bool Playing, string Title, string Artist)
{
    public static MediaInfo Nothing { get; } = new(false, "", "");
}

/// <summary>
/// Volumen del sistema. En Windows: Core Audio (<c>IAudioEndpointVolume</c>). Ningún miembro lanza: si la API falla,
/// se apunta en el log y el estado queda en 0 / sin silenciar (spec §5).
/// </summary>
public interface IVolumeController
{
    VolumeInfo Get();

    /// <summary>Fija el volumen (0–100).</summary>
    void SetLevel(int level);

    void SetMuted(bool muted);

    /// <summary>El volumen o el silencio han cambiado en el PC (desde cualquier hilo).</summary>
    event Action? Changed;
}

/// <summary>
/// Música del sistema. En Windows: sesiones multimedia (WinRT) para leer el estado y teclas multimedia para las órdenes.
/// <see cref="Current"/> no lanza; <see cref="Send"/> sí si el sistema rechaza la orden (se informa al móvil como error).
/// </summary>
public interface IMediaController
{
    MediaInfo Current();

    void Send(MediaCommand command);

    /// <summary>Ha cambiado la canción o el estado de reproducción (desde cualquier hilo).</summary>
    event Action? Changed;
}

/// <summary>Widgets del panel del móvil: su lista, los toques y el estado de volumen y música.</summary>
public interface IWidgetService
{
    /// <summary>Widgets actuales con sus ids, para el <c>WIDGET_LAYOUT</c>.</summary>
    WidgetLayoutMessage CurrentLayout();

    /// <summary>Estado actual, o <c>null</c> si no hay widget de volumen.</summary>
    VolumeStateMessage? CurrentVolume();

    /// <summary>Estado actual, o <c>null</c> si no hay widget multimedia.</summary>
    MediaStateMessage? CurrentMedia();

    /// <summary>Id del widget de volumen, o <c>null</c> si no hay.</summary>
    byte? VolumeWidgetId { get; }

    /// <summary>Id del widget multimedia, o <c>null</c> si no hay.</summary>
    byte? MediaWidgetId { get; }

    /// <summary>Ejecuta el botón <paramref name="id"/>. Error si no existe o no es un botón.</summary>
    GestureStatus Press(byte id);

    /// <summary>Fija el volumen; recorta a 0–100.</summary>
    void SetVolume(int level);

    void SetMuted(bool muted);

    void SendMedia(MediaCommand command);

    /// <summary>Se ha recargado la configuración y la lista de widgets es otra.</summary>
    event Action? LayoutChanged;

    /// <summary>El volumen ha cambiado en el PC (como mucho cada 100 ms; el último gana).</summary>
    event Action<VolumeStateMessage>? VolumeChanged;

    /// <summary>La música ha cambiado en el PC (como mucho cada 100 ms; el último gana).</summary>
    event Action<MediaStateMessage>? MediaChanged;
}
