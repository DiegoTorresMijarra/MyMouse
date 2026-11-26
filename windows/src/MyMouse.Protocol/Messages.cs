namespace MyMouse.Protocol;

/// <summary>Constantes del protocolo. Ver protocol/PROTOCOL.md.</summary>
public static class ProtocolInfo
{
    public const byte CurrentVersion = 1;
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
    // 0x06 Gesture: reservado para la versión 2 (v1 de la app).
    HelloAck = 0x81,
    Pong = 0x85,
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
