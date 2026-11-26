namespace MyMouse.Protocol;

/// <summary>Constantes del protocolo. Ver protocol/PROTOCOL.md.</summary>
public static class ProtocolInfo
{
    /// <summary>Versión más alta soportada. 2 = modo gestos.</summary>
    public const byte CurrentVersion = 2;

    /// <summary>Primera versión con los mensajes del modo gestos.</summary>
    public const byte GesturesVersion = 2;

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
    HelloAck = 0x81,
    Pong = 0x85,
    GestureResult = 0x86,
    GestureList = 0x87,
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
