namespace MyMouse.Protocol;

/// <summary>Constantes del protocolo. Ver protocol/PROTOCOL.md.</summary>
public static class ProtocolInfo
{
    /// <summary>Versión más alta soportada. 2 = modo gestos, 3 = panel de widgets, 4 = gestos de Windows (SYSTEM_GESTURE).</summary>
    public const byte CurrentVersion = 4;

    /// <summary>Primera versión con los mensajes del modo gestos.</summary>
    public const byte GesturesVersion = 2;

    /// <summary>Primera versión con los mensajes del panel de widgets.</summary>
    public const byte WidgetsVersion = 3;

    /// <summary>Primera versión con SYSTEM_GESTURE (gestos de 3 dedos de Windows).</summary>
    public const byte SystemGestureVersion = 4;

    /// <summary>Máximo de widgets en un WIDGET_LAYOUT.</summary>
    public const int MaxWidgets = 32;

    /// <summary>Longitud máxima del nombre de una forma, en bytes UTF-8.</summary>
    public const int MaxGestureNameBytes = 64;

    /// <summary>Máximo de entradas en un GESTURE_LIST.</summary>
    public const int MaxGestureListEntries = 64;
    public const int DefaultPort = 47800;

    /// <summary>Tiempo sin recibir nada tras el cual la conexión se da por muerta.</summary>
    public static readonly TimeSpan IdleTimeout = TimeSpan.FromSeconds(3);
}

public enum MessageType : byte
{
    Hello = 0x01,
    Move = 0x02,
    Button = 0x03,
    Scroll = 0x04,
    Ping = 0x05,
    Gesture = 0x06,
    Zoom = 0x07,
    WidgetPress = 0x08,
    VolumeSet = 0x09,
    MediaCommand = 0x0A,
    VolumeMute = 0x0B,
    SystemGesture = 0x0C,
    HelloAck = 0x81,
    Pong = 0x85,
    GestureResult = 0x86,
    GestureList = 0x87,
    WidgetLayout = 0x88,
    VolumeState = 0x89,
    MediaState = 0x8A,
    WidgetResult = 0x8B,
}

public enum MouseButton : byte
{
    Left = 0,
    Right = 1,
    Middle = 2,
}

public abstract record Message
{
    public abstract MessageType Type { get; }
}

public sealed record HelloMessage(byte Version) : Message
{
    public override MessageType Type => MessageType.Hello;
}

public sealed record HelloAckMessage(byte Version) : Message
{
    public override MessageType Type => MessageType.HelloAck;
}

/// <summary>Desplazamiento relativo en píxeles, ya acelerado por el cliente.</summary>
public sealed record MoveMessage(short Dx, short Dy) : Message
{
    public override MessageType Type => MessageType.Move;
}

public sealed record ButtonMessage(MouseButton Button, bool Pressed) : Message
{
    public override MessageType Type => MessageType.Button;
}

/// <summary>Unidades de rueda de Windows (120 = un paso). Vertical positivo = arriba; horizontal positivo = derecha.</summary>
public sealed record ScrollMessage(short Vertical, short Horizontal) : Message
{
    public override MessageType Type => MessageType.Scroll;
}

/// <summary>Zoom en unidades de rueda (120 = un paso). Positivo = acercar. El host lo ejecuta como Ctrl + rueda.</summary>
public sealed record ZoomMessage(short Delta) : Message
{
    public override MessageType Type => MessageType.Zoom;
}

/// <summary>Forma reconocida en el móvil (p. ej. "circle", "custom:firma").</summary>
public sealed record GestureMessage(string Name) : Message
{
    public override MessageType Type => MessageType.Gesture;
}

public enum GestureStatus : byte
{
    Executed = 0,
    Unassigned = 1,
    Error = 2,
}

public sealed record GestureResultMessage(GestureStatus Status) : Message
{
    public override MessageType Type => MessageType.GestureResult;
}

public sealed record GestureListEntry(string Name, string Help, bool Pinned);

/// <summary>Formas con acción asignada, para la chuleta del móvil. <paramref name="Context"/>: app activa con reglas propias, o vacío.</summary>
public sealed record GestureListMessage(string Context, IReadOnlyList<GestureListEntry> Entries) : Message
{
    public override MessageType Type => MessageType.GestureList;

    // Igualdad por contenido (los records comparan listas por referencia).
    public bool Equals(GestureListMessage? other) =>
        other is not null && Context == other.Context && Entries.SequenceEqual(other.Entries);

    public override int GetHashCode() => HashCode.Combine(Context, Entries.Count);
}

/// <summary>Tipo de un widget en el <see cref="WidgetLayoutMessage"/>.</summary>
public enum WidgetKind : byte
{
    Button = 0,
    Media = 1,
    Volume = 2,
}

/// <summary>Orden multimedia de <see cref="MediaCommandMessage"/>.</summary>
public enum MediaCommand : byte
{
    PlayPause = 0,
    Next = 1,
    Previous = 2,
}

/// <summary>Toque en un widget de tipo botón. <paramref name="Id"/> es su posición en el último WIDGET_LAYOUT.</summary>
public sealed record WidgetPressMessage(byte Id) : Message
{
    public override MessageType Type => MessageType.WidgetPress;
}

/// <summary>Nivel de volumen pedido (0–100). El decodificador acepta cualquier byte: el host recorta a 100.</summary>
public sealed record VolumeSetMessage(byte Level) : Message
{
    public override MessageType Type => MessageType.VolumeSet;
}

public sealed record MediaCommandMessage(MediaCommand Command) : Message
{
    public override MessageType Type => MessageType.MediaCommand;
}

public sealed record VolumeMuteMessage(bool Muted) : Message
{
    public override MessageType Type => MessageType.VolumeMute;
}

/// <summary>Gesto de sistema de Windows de <see cref="SystemGestureMessage"/>. El cambio de app mantiene Alt pulsado entre Start y End.</summary>
public enum SystemGestureCommand : byte
{
    TaskView = 0,
    ShowDesktop = 1,
    AppSwitchStart = 2,
    AppSwitchNext = 3,
    AppSwitchPrevious = 4,
    AppSwitchEnd = 5,
}

/// <summary>Gesto de 3 dedos del móvil: vista de tareas, escritorio o cambio de app (protocolo v4).</summary>
public sealed record SystemGestureMessage(SystemGestureCommand Command) : Message
{
    public override MessageType Type => MessageType.SystemGesture;
}

public sealed record WidgetLayoutEntry(byte Id, WidgetKind Kind, string Label, string Icon);

/// <summary>Widgets configurados en el PC, para dibujar el panel del móvil. Un layout nuevo invalida los ids anteriores.</summary>
public sealed record WidgetLayoutMessage(IReadOnlyList<WidgetLayoutEntry> Entries) : Message
{
    public override MessageType Type => MessageType.WidgetLayout;

    // Igualdad por contenido (los records comparan listas por referencia).
    public bool Equals(WidgetLayoutMessage? other) => other is not null && Entries.SequenceEqual(other.Entries);

    public override int GetHashCode() => Entries.Count;
}

/// <summary>Volumen del sistema (0–100) y si está silenciado.</summary>
public sealed record VolumeStateMessage(byte Level, bool Muted) : Message
{
    public override MessageType Type => MessageType.VolumeState;
}

/// <summary>Lo que suena en el PC. Título y artista vacíos si no suena nada.</summary>
public sealed record MediaStateMessage(bool Playing, string Title, string Artist) : Message
{
    public override MessageType Type => MessageType.MediaState;
}

/// <summary>Resultado de un <see cref="WidgetPressMessage"/> (o de una orden rechazada). Mismos valores que <see cref="GestureStatus"/>.</summary>
public sealed record WidgetResultMessage(byte Id, GestureStatus Status) : Message
{
    public override MessageType Type => MessageType.WidgetResult;
}

public sealed record PingMessage : Message
{
    public static readonly PingMessage Instance = new();
    public override MessageType Type => MessageType.Ping;
}

public sealed record PongMessage : Message
{
    public static readonly PongMessage Instance = new();
    public override MessageType Type => MessageType.Pong;
}

public sealed class ProtocolException(string message) : Exception(message);
