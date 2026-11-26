using System.Buffers.Binary;
using System.Text;

namespace MyMouse.Protocol;

/// <summary>
/// Codificación y decodificación binaria de mensajes (little-endian).
/// Tipos de tamaño fijo: type + payload. Tipos variables: type + u16 longitud + payload.
/// </summary>
public static class MessageCodec
{
    /// <summary>Tamaño fijo del payload, o <c>null</c> si el tipo es variable. Lanza si el tipo es desconocido.</summary>
    public static int? PayloadSize(byte type) => (MessageType)type switch
    {
        MessageType.Hello or MessageType.HelloAck or MessageType.GestureResult
            or MessageType.WidgetPress or MessageType.VolumeSet or MessageType.MediaCommand or MessageType.VolumeMute => 1,
        MessageType.VolumeState or MessageType.WidgetResult => 2,
        MessageType.Move or MessageType.Scroll => 4,
        MessageType.Button or MessageType.Zoom => 2,
        MessageType.Ping or MessageType.Pong => 0,
        MessageType.Gesture or MessageType.GestureList or MessageType.WidgetLayout or MessageType.MediaState => null,
        _ => throw new ProtocolException($"Tipo de mensaje desconocido: 0x{type:X2}"),
    };

    public static byte[] Encode(Message message)
    {
        var payload = EncodePayload(message);
        var variable = PayloadSize((byte)message.Type) is null;
        if (variable && payload.Length > ushort.MaxValue)
            throw new ArgumentException("Payload demasiado grande", nameof(message));

        var header = variable ? 3 : 1;
        var buffer = new byte[header + payload.Length];
        buffer[0] = (byte)message.Type;
        if (variable) BinaryPrimitives.WriteUInt16LittleEndian(buffer.AsSpan(1), (ushort)payload.Length);
        payload.CopyTo(buffer, header);
        return buffer;
    }

    private static byte[] EncodePayload(Message message)
    {
        switch (message)
        {
            case HelloMessage m:
                return [m.Version];
            case HelloAckMessage m:
                return [m.Version];
            case MoveMessage m:
                return I16Pair(m.Dx, m.Dy);
            case ButtonMessage m:
                return [(byte)m.Button, m.Pressed ? (byte)1 : (byte)0];
            case ScrollMessage m:
                return I16Pair(m.Vertical, m.Horizontal);
            case ZoomMessage m:
            {
                var b = new byte[2];
                BinaryPrimitives.WriteInt16LittleEndian(b, m.Delta);
                return b;
            }
            case GestureMessage m:
            {
                var name = Encoding.UTF8.GetBytes(m.Name);
                if (name.Length is 0 or > ProtocolInfo.MaxGestureNameBytes)
                    throw new ArgumentException($"Nombre de forma inválido: '{m.Name}'", nameof(message));
                return name;
            }
            case GestureResultMessage m:
                return [(byte)m.Status];
            case GestureListMessage m:
            {
                var ms = new MemoryStream();
                WriteStr8(ms, m.Context);
                var entries = m.Entries.Take(ProtocolInfo.MaxGestureListEntries).ToList();
                ms.WriteByte((byte)entries.Count);
                foreach (var e in entries)
                {
                    ms.WriteByte(e.Pinned ? (byte)1 : (byte)0);
                    WriteStr8(ms, e.Name);
                    WriteStr8(ms, e.Help);
                }
                return ms.ToArray();
            }
            case WidgetPressMessage m:
                return [m.Id];
            case VolumeSetMessage m:
                return [m.Level];
            case MediaCommandMessage m:
                return [(byte)m.Command];
            case VolumeMuteMessage m:
                return [m.Muted ? (byte)1 : (byte)0];
            case WidgetLayoutMessage m:
            {
                var ms = new MemoryStream();
                var entries = m.Entries.Take(ProtocolInfo.MaxWidgets).ToList();
                ms.WriteByte((byte)entries.Count);
                foreach (var e in entries)
                {
                    ms.WriteByte(e.Id);
                    ms.WriteByte((byte)e.Kind);
                    WriteStr8(ms, e.Label);
                    WriteStr8(ms, e.Icon);
                }
                return ms.ToArray();
            }
            case VolumeStateMessage m:
                return [Math.Min(m.Level, (byte)100), m.Muted ? (byte)1 : (byte)0];
            case MediaStateMessage m:
            {
                var ms = new MemoryStream();
                ms.WriteByte(m.Playing ? (byte)1 : (byte)0);
                WriteStr8(ms, m.Title);
                WriteStr8(ms, m.Artist);
                return ms.ToArray();
            }
            case WidgetResultMessage m:
                return [m.Id, (byte)m.Status];
            case PingMessage or PongMessage:
                return [];
            default:
                throw new ArgumentException($"Mensaje no soportado: {message.GetType().Name}", nameof(message));
        }
    }

    public static Message Decode(byte type, ReadOnlySpan<byte> payload)
    {
        var expected = PayloadSize(type);
        if (expected is { } size && payload.Length != size)
            throw new ProtocolException($"Payload de 0x{type:X2} debe tener {size} bytes, tiene {payload.Length}");

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
            MessageType.Gesture => DecodeGesture(payload),
            MessageType.GestureResult => new GestureResultMessage(ParseStatus(payload[0])),
            MessageType.GestureList => DecodeGestureList(payload),
            MessageType.WidgetPress => new WidgetPressMessage(payload[0]),
            MessageType.VolumeSet => new VolumeSetMessage(payload[0]),
            MessageType.MediaCommand => new MediaCommandMessage(ParseMediaCommand(payload[0])),
            MessageType.VolumeMute => new VolumeMuteMessage(ParseBool(payload[0])),
            MessageType.WidgetLayout => DecodeWidgetLayout(payload),
            MessageType.VolumeState => new VolumeStateMessage(ParseLevel(payload[0]), ParseBool(payload[1])),
            MessageType.MediaState => DecodeMediaState(payload),
            MessageType.WidgetResult => new WidgetResultMessage(payload[0], ParseStatus(payload[1])),
            _ => throw new ProtocolException($"Tipo de mensaje desconocido: 0x{type:X2}"),
        };
    }

    private static GestureMessage DecodeGesture(ReadOnlySpan<byte> payload)
    {
        if (payload.Length is 0 or > ProtocolInfo.MaxGestureNameBytes)
            throw new ProtocolException($"Nombre de forma de {payload.Length} bytes");
        return new GestureMessage(DecodeUtf8(payload));
    }

    private static GestureListMessage DecodeGestureList(ReadOnlySpan<byte> payload)
    {
        var pos = 0;
        var context = ReadStr8(payload, ref pos);
        var count = ReadByte(payload, ref pos);
        var entries = new List<GestureListEntry>(count);
        for (var i = 0; i < count; i++)
        {
            var flags = ReadByte(payload, ref pos);
            var name = ReadStr8(payload, ref pos);
            var help = ReadStr8(payload, ref pos);
            entries.Add(new GestureListEntry(name, help, (flags & 1) != 0));
        }
        if (pos != payload.Length)
            throw new ProtocolException("Bytes sobrantes en GESTURE_LIST");
        return new GestureListMessage(context, entries);
    }

    private static WidgetLayoutMessage DecodeWidgetLayout(ReadOnlySpan<byte> payload)
    {
        var pos = 0;
        var count = ReadByte(payload, ref pos);
        if (count > ProtocolInfo.MaxWidgets)
            throw new ProtocolException($"WIDGET_LAYOUT con {count} widgets");
        var entries = new List<WidgetLayoutEntry>(count);
        for (var i = 0; i < count; i++)
        {
            var id = ReadByte(payload, ref pos);
            var kind = ReadByte(payload, ref pos);
            if (kind > (byte)WidgetKind.Volume)
                throw new ProtocolException($"Tipo de widget inválido: {kind}");
            var label = ReadStr8(payload, ref pos);
            var icon = ReadStr8(payload, ref pos);
            entries.Add(new WidgetLayoutEntry(id, (WidgetKind)kind, label, icon));
        }
        if (pos != payload.Length)
            throw new ProtocolException("Bytes sobrantes en WIDGET_LAYOUT");
        return new WidgetLayoutMessage(entries);
    }

    private static MediaStateMessage DecodeMediaState(ReadOnlySpan<byte> payload)
    {
        var pos = 0;
        var playing = ParseBool(ReadByte(payload, ref pos));
        var title = ReadStr8(payload, ref pos);
        var artist = ReadStr8(payload, ref pos);
        if (pos != payload.Length)
            throw new ProtocolException("Bytes sobrantes en MEDIA_STATE");
        return new MediaStateMessage(playing, title, artist);
    }

    private static byte ReadByte(ReadOnlySpan<byte> payload, ref int pos) =>
        pos < payload.Length ? payload[pos++] : throw new ProtocolException("Mensaje truncado");

    private static string ReadStr8(ReadOnlySpan<byte> payload, ref int pos)
    {
        int len = ReadByte(payload, ref pos);
        if (pos + len > payload.Length) throw new ProtocolException("Mensaje truncado");
        var s = DecodeUtf8(payload.Slice(pos, len));
        pos += len;
        return s;
    }

    /// <summary>Escribe un str8, recortando a 255 bytes sin partir caracteres.</summary>
    private static void WriteStr8(Stream s, string text)
    {
        var bytes = Encoding.UTF8.GetBytes(text);
        var len = Math.Min(bytes.Length, 255);
        while (len < bytes.Length && len > 0 && (bytes[len] & 0xC0) == 0x80) len--; // no cortar a mitad de un carácter
        s.WriteByte((byte)len);
        s.Write(bytes, 0, len);
    }

    private static readonly UTF8Encoding StrictUtf8 = new(encoderShouldEmitUTF8Identifier: false, throwOnInvalidBytes: true);

    private static string DecodeUtf8(ReadOnlySpan<byte> bytes)
    {
        try
        {
            return StrictUtf8.GetString(bytes);
        }
        catch (DecoderFallbackException)
        {
            throw new ProtocolException("Texto UTF-8 inválido");
        }
    }

    private static byte[] I16Pair(short a, short b)
    {
        var buffer = new byte[4];
        BinaryPrimitives.WriteInt16LittleEndian(buffer, a);
        BinaryPrimitives.WriteInt16LittleEndian(buffer.AsSpan(2), b);
        return buffer;
    }

    private static MouseButton ParseButton(byte value) => value <= (byte)MouseButton.Middle
        ? (MouseButton)value
        : throw new ProtocolException($"Botón inválido: {value}");

    private static GestureStatus ParseStatus(byte value) => value <= (byte)GestureStatus.Error
        ? (GestureStatus)value
        : throw new ProtocolException($"Estado de forma inválido: {value}");

    private static MediaCommand ParseMediaCommand(byte value) => value <= (byte)MediaCommand.Previous
        ? (MediaCommand)value
        : throw new ProtocolException($"Orden multimedia inválida: {value}");

    private static byte ParseLevel(byte value) => value <= 100
        ? value
        : throw new ProtocolException($"Nivel de volumen inválido: {value}");

    private static bool ParseBool(byte value) => value switch
    {
        0 => false,
        1 => true,
        _ => throw new ProtocolException($"Valor booleano inválido: {value}"),
    };
}
