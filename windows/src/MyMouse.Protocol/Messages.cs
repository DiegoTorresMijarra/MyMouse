namespace MyMouse.Protocol;

/// <summary>Constantes del protocolo. Ver protocol/PROTOCOL.md.</summary>
public static class ProtocolInfo
{
    /// <summary>Versión más alta soportada. 2 = modo gestos, 3 = panel de widgets, 4 = gestos de Windows (SYSTEM_GESTURE), 5 = toolbox del PC, 6 = versión de la app (APP_VERSION).</summary>
    public const byte CurrentVersion = 6;

    /// <summary>Primera versión con APP_VERSION (avisos de versión cruzados entre móvil y PC).</summary>
    public const byte AppVersionVersion = 6;

    /// <summary>Longitud máxima del texto de versión de APP_VERSION, en bytes UTF-8.</summary>
    public const int MaxAppVersionBytes = 64;

    /// <summary>Primera versión con los mensajes del modo gestos.</summary>
    public const byte GesturesVersion = 2;

    /// <summary>Primera versión con los mensajes del panel de widgets.</summary>
    public const byte WidgetsVersion = 3;

    /// <summary>Primera versión con SYSTEM_GESTURE (gestos de 3 dedos de Windows).</summary>
    public const byte SystemGestureVersion = 4;

    /// <summary>Primera versión con TOOLBOX, el gesto en vivo (GESTURE_MODE, STROKE_*) y la sincronización de formas (SHAPE_*).</summary>
    public const byte ToolboxVersion = 5;

    /// <summary>Puntos a los que el móvil remuestrea cada muestra de una forma en SHAPE_INFO.</summary>
    public const int ShapeSamplePoints = 32;

    /// <summary>Máximo de muestras por forma y de puntos por muestra o lote (los cuenta un u8).</summary>
    public const int MaxShapeSamples = 255;
    public const int MaxPointsPerBatch = 255;

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
    Toolbox = 0x0D,
    GestureMode = 0x0E,
    StrokePoints = 0x0F,
    StrokeEnd = 0x10,
    ShapeInfo = 0x11,
    ShapeListEnd = 0x12,
    ShapeResult = 0x13,
    AppVersion = 0x14,
    HelloAck = 0x81,
    Pong = 0x85,
    GestureResult = 0x86,
    GestureList = 0x87,
    WidgetLayout = 0x88,
    VolumeState = 0x89,
    MediaState = 0x8A,
    WidgetResult = 0x8B,
    ShapeAdd = 0x8C,
    ShapeDelete = 0x8D,
    AppVersionHost = 0x8E,
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

/// <summary>Punto normalizado 0–65535 (trazos y muestras de formas del protocolo v5).</summary>
public readonly record struct StrokePoint(ushort X, ushort Y);

/// <summary>Cómo terminó un trazo del modo gestos (STROKE_END).</summary>
public enum StrokeResult : byte
{
    Recognized = 0,
    NotRecognized = 1,
    Cancelled = 2,
    TimedOut = 3,
}

/// <summary>Resultado de añadir o borrar una forma en el móvil (SHAPE_RESULT).</summary>
public enum ShapeStatus : byte
{
    Saved = 0,
    Clashes = 1,
    DuplicateName = 2,
    Invalid = 3,
    Deleted = 4,
    NotFound = 5,
}

/// <summary>Mostrar u ocultar la toolbox del PC: 4 dedos arriba / abajo en el móvil (protocolo v5).</summary>
public sealed record ToolboxMessage(bool Show) : Message
{
    public override MessageType Type => MessageType.Toolbox;
}

/// <summary>El modo gestos se activa o termina en el móvil. <paramref name="StartTimeoutMs"/>: tiempo para empezar a dibujar.</summary>
public sealed record GestureModeMessage(bool Active, ushort StartTimeoutMs) : Message
{
    public override MessageType Type => MessageType.GestureMode;
}

/// <summary>Lote de puntos del trazo en curso, normalizados 0–65535 (escala común en los dos ejes).</summary>
public sealed record StrokePointsMessage(IReadOnlyList<StrokePoint> Points) : Message
{
    public override MessageType Type => MessageType.StrokePoints;

    public bool Equals(StrokePointsMessage? other) => other is not null && Points.SequenceEqual(other.Points);

    public override int GetHashCode() => Points.Count;
}

public sealed record StrokeEndMessage(StrokeResult Result) : Message
{
    public override MessageType Type => MessageType.StrokeEnd;
}

/// <summary>Una forma propia del móvil. Cada muestra son 32 puntos normalizados.</summary>
public sealed record ShapeInfoMessage(string Name, IReadOnlyList<IReadOnlyList<StrokePoint>> Samples) : Message
{
    public override MessageType Type => MessageType.ShapeInfo;

    public bool Equals(ShapeInfoMessage? other) =>
        other is not null && Name == other.Name && Samples.Count == other.Samples.Count &&
        Samples.Zip(other.Samples).All(p => p.First.SequenceEqual(p.Second));

    public override int GetHashCode() => HashCode.Combine(Name, Samples.Count);
}

/// <summary>Fin de la lista de formas propias; <paramref name="Count"/> = cuántas se han enviado.</summary>
public sealed record ShapeListEndMessage(byte Count) : Message
{
    public override MessageType Type => MessageType.ShapeListEnd;
}

/// <summary>Respuesta del móvil a SHAPE_ADD o SHAPE_DELETE. <paramref name="Other"/>: forma con la que se confunde, o vacío.</summary>
public sealed record ShapeResultMessage(string Name, ShapeStatus Status, string Other) : Message
{
    public override MessageType Type => MessageType.ShapeResult;
}

/// <summary>El PC añade una forma grabada con el ratón. <paramref name="Force"/>: guardarla aunque se confunda con otra.</summary>
public sealed record ShapeAddMessage(string Name, IReadOnlyList<IReadOnlyList<StrokePoint>> Samples, bool Force) : Message
{
    public override MessageType Type => MessageType.ShapeAdd;

    public bool Equals(ShapeAddMessage? other) =>
        other is not null && Name == other.Name && Force == other.Force && Samples.Count == other.Samples.Count &&
        Samples.Zip(other.Samples).All(p => p.First.SequenceEqual(p.Second));

    public override int GetHashCode() => HashCode.Combine(Name, Samples.Count);
}

public sealed record ShapeDeleteMessage(string Name) : Message
{
    public override MessageType Type => MessageType.ShapeDelete;
}

/// <summary>Versión de la app del móvil (0x14, móvil a PC; protocolo v6), p. ej. "5.0".</summary>
public sealed record AppVersionMessage(string Version) : Message
{
    public override MessageType Type => MessageType.AppVersion;
}

/// <summary>Versión de la app del PC (0x8E, PC a móvil; protocolo v6).</summary>
public sealed record HostAppVersionMessage(string Version) : Message
{
    public override MessageType Type => MessageType.AppVersionHost;
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
