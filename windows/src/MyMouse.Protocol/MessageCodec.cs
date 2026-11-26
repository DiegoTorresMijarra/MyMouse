using System.Buffers.Binary;

namespace MyMouse.Protocol;

/// <summary>Codificación y decodificación binaria de mensajes (little-endian, payload de tamaño fijo).</summary>
public static class MessageCodec
{
    /// <summary>Tamaño del payload (sin contar el byte de tipo). Lanza <see cref="ProtocolException"/> si el tipo es desconocido.</summary>
    public static int PayloadSize(byte type) => (MessageType)type switch
    {
        MessageType.Hello or MessageType.HelloAck => 1,
        MessageType.Move or MessageType.Scroll => 4,
        MessageType.Button or MessageType.Zoom => 2,
        MessageType.Ping or MessageType.Pong => 0,
        _ => throw new ProtocolException($"Tipo de mensaje desconocido: 0x{type:X2}"),
    };

    public static byte[] Encode(Message message)
    {
        var buffer = new byte[1 + PayloadSize((byte)message.Type)];
        buffer[0] = (byte)message.Type;
        var payload = buffer.AsSpan(1);

        switch (message)
        {
            case HelloMessage m:
                payload[0] = m.Version;
                break;
            case HelloAckMessage m:
                payload[0] = m.Version;
                break;
            case MoveMessage m:
                BinaryPrimitives.WriteInt16LittleEndian(payload, m.Dx);
                BinaryPrimitives.WriteInt16LittleEndian(payload[2..], m.Dy);
                break;
            case ButtonMessage m:
                payload[0] = (byte)m.Button;
                payload[1] = m.Pressed ? (byte)1 : (byte)0;
                break;
            case ScrollMessage m:
                BinaryPrimitives.WriteInt16LittleEndian(payload, m.Vertical);
                BinaryPrimitives.WriteInt16LittleEndian(payload[2..], m.Horizontal);
                break;
            case ZoomMessage m:
                BinaryPrimitives.WriteInt16LittleEndian(payload, m.Delta);
                break;
            case PingMessage or PongMessage:
                break;
            default:
                throw new ArgumentException($"Mensaje no soportado: {message.GetType().Name}", nameof(message));
        }

        return buffer;
    }

    public static Message Decode(byte type, ReadOnlySpan<byte> payload)
    {
        var expected = PayloadSize(type);
        if (payload.Length != expected)
            throw new ProtocolException($"Payload de 0x{type:X2} debe tener {expected} bytes, tiene {payload.Length}");

        return (MessageType)type switch
        {
            MessageType.Hello => new HelloMessage(payload[0]),
            MessageType.HelloAck => new HelloAckMessage(payload[0]),
            MessageType.Move => new MoveMessage(
                BinaryPrimitives.ReadInt16LittleEndian(payload),
                BinaryPrimitives.ReadInt16LittleEndian(payload[2..])),
            MessageType.Button => new ButtonMessage(ParseButton(payload[0]), ParseBool(payload[1])),
            MessageType.Scroll => new ScrollMessage(
                BinaryPrimitives.ReadInt16LittleEndian(payload),
                BinaryPrimitives.ReadInt16LittleEndian(payload[2..])),
            MessageType.Zoom => new ZoomMessage(BinaryPrimitives.ReadInt16LittleEndian(payload)),
            MessageType.Ping => PingMessage.Instance,
            MessageType.Pong => PongMessage.Instance,
            _ => throw new ProtocolException($"Tipo de mensaje desconocido: 0x{type:X2}"),
        };
    }

    private static MouseButton ParseButton(byte value) => value <= (byte)MouseButton.Middle
        ? (MouseButton)value
        : throw new ProtocolException($"Botón inválido: {value}");

    private static bool ParseBool(byte value) => value switch
    {
        0 => false,
        1 => true,
        _ => throw new ProtocolException($"Valor booleano inválido: {value}"),
    };
}
