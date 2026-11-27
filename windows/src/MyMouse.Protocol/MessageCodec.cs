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
            or MessageType.WidgetPress or MessageType.VolumeSet or MessageType.MediaCommand or MessageType.VolumeMute
            or MessageType.SystemGesture or MessageType.Toolbox or MessageType.StrokeEnd or MessageType.ShapeListEnd
            or MessageType.KeyboardMode or MessageType.KeyboardLeds => 1,
        MessageType.GestureMode => 3,
        MessageType.VolumeState or MessageType.WidgetResult => 2,
        MessageType.Move or MessageType.Scroll => 4,
        MessageType.Button or MessageType.Zoom or MessageType.Key => 2,
        MessageType.Ping or MessageType.Pong or MessageType.KeyboardOpen => 0,
        MessageType.Gesture or MessageType.GestureList or MessageType.WidgetLayout or MessageType.MediaState
            or MessageType.StrokePoints or MessageType.ShapeInfo or MessageType.ShapeResult
            or MessageType.ShapeAdd or MessageType.ShapeDelete
            or MessageType.AppVersion or MessageType.AppVersionHost or MessageType.KeyboardLayout
            or MessageType.Theme or MessageType.HostTheme => null,
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
            case SystemGestureMessage m:
                return [(byte)m.Command];
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
            case ToolboxMessage m:
                return [m.Show ? (byte)1 : (byte)0];
            case GestureModeMessage m:
            {
                var b = new byte[3];
                b[0] = m.Active ? (byte)1 : (byte)0;
                BinaryPrimitives.WriteUInt16LittleEndian(b.AsSpan(1), m.StartTimeoutMs);
                return b;
            }
            case StrokePointsMessage m:
            {
                var ms = new MemoryStream();
                WritePoints(ms, m.Points);
                return ms.ToArray();
            }
            case StrokeEndMessage m:
                return [(byte)m.Result];
            case ShapeInfoMessage m:
                return EncodeShape(m.Name, m.Samples, force: null);
            case ShapeListEndMessage m:
                return [m.Count];
            case ShapeResultMessage m:
            {
                var ms = new MemoryStream();
                WriteStr8(ms, m.Name);
                ms.WriteByte((byte)m.Status);
                WriteStr8(ms, m.Other);
                return ms.ToArray();
            }
            case ShapeAddMessage m:
                return EncodeShape(m.Name, m.Samples, m.Force);
            case ShapeDeleteMessage m:
            {
                var ms = new MemoryStream();
                WriteStr8(ms, m.Name);
                return ms.ToArray();
            }
            case AppVersionMessage m:
                return EncodeAppVersion(m.Version);
            case HostAppVersionMessage m:
                return EncodeAppVersion(m.Version);
            case KeyMessage m:
                return [m.Usage, m.Down ? (byte)1 : (byte)0];
            case KeyboardModeMessage m:
                return [m.On ? (byte)1 : (byte)0];
            case KeyboardLedsMessage m:
                return [m.Bits];
            case KeyboardLayoutMessage m:
                return EncodeLayoutId(m.Id);
            case ThemeMessage m:
                return EncodeTheme(m);
            case PingMessage or PongMessage or KeyboardOpenMessage:
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
            MessageType.SystemGesture => new SystemGestureMessage(ParseSystemGesture(payload[0])),
            MessageType.WidgetLayout => DecodeWidgetLayout(payload),
            MessageType.VolumeState => new VolumeStateMessage(ParseLevel(payload[0]), ParseBool(payload[1])),
            MessageType.MediaState => DecodeMediaState(payload),
            MessageType.WidgetResult => new WidgetResultMessage(payload[0], ParseStatus(payload[1])),
            MessageType.Toolbox => new ToolboxMessage(ParseBool(payload[0])),
            MessageType.GestureMode => new GestureModeMessage(ParseBool(payload[0]), BinaryPrimitives.ReadUInt16LittleEndian(payload[1..])),
            MessageType.StrokePoints => DecodeStrokePoints(payload),
            MessageType.StrokeEnd => new StrokeEndMessage(ParseStrokeResult(payload[0])),
            MessageType.ShapeInfo => DecodeShape(payload, withForce: false),
            MessageType.ShapeListEnd => new ShapeListEndMessage(payload[0]),
            MessageType.ShapeResult => DecodeShapeResult(payload),
            MessageType.ShapeAdd => DecodeShape(payload, withForce: true),
            MessageType.ShapeDelete => DecodeShapeDelete(payload),
            MessageType.AppVersion => new AppVersionMessage(DecodeAppVersion(payload)),
            MessageType.AppVersionHost => new HostAppVersionMessage(DecodeAppVersion(payload)),
            MessageType.Key => new KeyMessage(payload[0], ParseBool(payload[1])),
            MessageType.KeyboardMode => new KeyboardModeMessage(ParseBool(payload[0])),
            MessageType.KeyboardOpen => KeyboardOpenMessage.Instance,
            MessageType.KeyboardLeds => new KeyboardLedsMessage(payload[0]),
            MessageType.KeyboardLayout => new KeyboardLayoutMessage(DecodeLayoutId(payload)),
            MessageType.Theme => DecodeTheme(payload, fromHost: false),
            MessageType.HostTheme => DecodeTheme(payload, fromHost: true),
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

    private static void WritePoints(Stream s, IReadOnlyList<StrokePoint> points)
    {
        if (points.Count > ProtocolInfo.MaxPointsPerBatch)
            throw new ArgumentException("Demasiados puntos");
        s.WriteByte((byte)points.Count);
        Span<byte> b = stackalloc byte[4];
        foreach (var p in points)
        {
            BinaryPrimitives.WriteUInt16LittleEndian(b, p.X);
            BinaryPrimitives.WriteUInt16LittleEndian(b[2..], p.Y);
            s.Write(b);
        }
    }

    private static byte[] EncodeShape(string name, IReadOnlyList<IReadOnlyList<StrokePoint>> samples, bool? force)
    {
        if (samples.Count > ProtocolInfo.MaxShapeSamples)
            throw new ArgumentException("Demasiadas muestras");
        var ms = new MemoryStream();
        WriteStr8(ms, name);
        ms.WriteByte((byte)samples.Count);
        foreach (var sample in samples) WritePoints(ms, sample);
        if (force is { } f) ms.WriteByte(f ? (byte)1 : (byte)0);
        return ms.ToArray();
    }

    private static List<StrokePoint> ReadPoints(ReadOnlySpan<byte> payload, ref int pos)
    {
        int n = ReadByte(payload, ref pos);
        if (pos + n * 4 > payload.Length) throw new ProtocolException("Mensaje truncado");
        var points = new List<StrokePoint>(n);
        for (var i = 0; i < n; i++)
        {
            points.Add(new StrokePoint(
                BinaryPrimitives.ReadUInt16LittleEndian(payload[pos..]),
                BinaryPrimitives.ReadUInt16LittleEndian(payload[(pos + 2)..])));
            pos += 4;
        }
        return points;
    }

    private static StrokePointsMessage DecodeStrokePoints(ReadOnlySpan<byte> payload)
    {
        var pos = 0;
        var points = ReadPoints(payload, ref pos);
        if (pos != payload.Length) throw new ProtocolException("Bytes sobrantes en STROKE_POINTS");
        if (points.Count == 0) throw new ProtocolException("STROKE_POINTS vacío");
        return new StrokePointsMessage(points);
    }

    private static Message DecodeShape(ReadOnlySpan<byte> payload, bool withForce)
    {
        var pos = 0;
        var name = ReadStr8(payload, ref pos);
        int count = ReadByte(payload, ref pos);
        var samples = new List<IReadOnlyList<StrokePoint>>(count);
        for (var i = 0; i < count; i++) samples.Add(ReadPoints(payload, ref pos));
        var force = withForce && ParseBool(ReadByte(payload, ref pos));
        if (pos != payload.Length) throw new ProtocolException($"Bytes sobrantes en {(withForce ? "SHAPE_ADD" : "SHAPE_INFO")}");
        return withForce ? new ShapeAddMessage(name, samples, force) : new ShapeInfoMessage(name, samples);
    }

    private static ShapeResultMessage DecodeShapeResult(ReadOnlySpan<byte> payload)
    {
        var pos = 0;
        var name = ReadStr8(payload, ref pos);
        var status = ReadByte(payload, ref pos);
        if (status > (byte)ShapeStatus.NotFound) throw new ProtocolException($"Estado de forma inválido: {status}");
        var other = ReadStr8(payload, ref pos);
        if (pos != payload.Length) throw new ProtocolException("Bytes sobrantes en SHAPE_RESULT");
        return new ShapeResultMessage(name, (ShapeStatus)status, other);
    }

    private static byte[] EncodeAppVersion(string version)
    {
        if (Encoding.UTF8.GetByteCount(version) > ProtocolInfo.MaxAppVersionBytes)
            throw new ArgumentException("Versión demasiado larga");
        var ms = new MemoryStream();
        WriteStr8(ms, version);
        return ms.ToArray();
    }

    private static string DecodeAppVersion(ReadOnlySpan<byte> payload)
    {
        var pos = 0;
        var version = ReadStr8(payload, ref pos);
        if (pos != payload.Length) throw new ProtocolException("Bytes sobrantes en APP_VERSION");
        if (Encoding.UTF8.GetByteCount(version) > ProtocolInfo.MaxAppVersionBytes)
            throw new ProtocolException("Versión demasiado larga en APP_VERSION");
        return version;
    }

    private static byte[] EncodeLayoutId(string id)
    {
        if (Encoding.UTF8.GetByteCount(id) > ProtocolInfo.MaxLayoutIdBytes)
            throw new ArgumentException("Id de distribución demasiado largo");
        var ms = new MemoryStream();
        WriteStr8(ms, id);
        return ms.ToArray();
    }

    private static string DecodeLayoutId(ReadOnlySpan<byte> payload)
    {
        var pos = 0;
        var id = ReadStr8(payload, ref pos);
        if (pos != payload.Length) throw new ProtocolException("Bytes sobrantes en KEYBOARD_LAYOUT");
        if (Encoding.UTF8.GetByteCount(id) > ProtocolInfo.MaxLayoutIdBytes)
            throw new ProtocolException("Id de distribución demasiado largo en KEYBOARD_LAYOUT");
        return id;
    }

    private static byte[] EncodeTheme(ThemeMessage m)
    {
        if (Encoding.UTF8.GetByteCount(m.ThemeId) > ProtocolInfo.MaxThemeIdBytes || Encoding.UTF8.GetByteCount(m.AccentId) > ProtocolInfo.MaxThemeIdBytes)
            throw new ArgumentException("Id de tema o de acento demasiado largo");
        if (m.ChangedAtMs < 0) throw new ArgumentException("Instante de cambio negativo");
        var ms = new MemoryStream();
        WriteStr8(ms, m.ThemeId);
        WriteStr8(ms, m.AccentId);
        ms.WriteByte(m.Stars ? (byte)1 : (byte)0);
        Span<byte> b = stackalloc byte[8];
        BinaryPrimitives.WriteInt64LittleEndian(b, m.ChangedAtMs);
        ms.Write(b);
        return ms.ToArray();
    }

    private static ThemeMessage DecodeTheme(ReadOnlySpan<byte> payload, bool fromHost)
    {
        var name = fromHost ? "THEME (PC)" : "THEME";
        var pos = 0;
        var theme = ReadStr8(payload, ref pos);
        var accent = ReadStr8(payload, ref pos);
        var stars = ParseBool(ReadByte(payload, ref pos));
        if (pos + 8 > payload.Length) throw new ProtocolException("Mensaje truncado");
        var changedAt = BinaryPrimitives.ReadInt64LittleEndian(payload[pos..]);
        pos += 8;
        if (pos != payload.Length) throw new ProtocolException($"Bytes sobrantes en {name}");
        if (Encoding.UTF8.GetByteCount(theme) > ProtocolInfo.MaxThemeIdBytes || Encoding.UTF8.GetByteCount(accent) > ProtocolInfo.MaxThemeIdBytes)
            throw new ProtocolException($"Id de tema o de acento demasiado largo en {name}");
        if (changedAt < 0) throw new ProtocolException($"Instante de cambio negativo en {name}");
        return new ThemeMessage(theme, accent, stars, changedAt, fromHost);
    }

    private static ShapeDeleteMessage DecodeShapeDelete(ReadOnlySpan<byte> payload)
    {
        var pos = 0;
        var name = ReadStr8(payload, ref pos);
        if (pos != payload.Length) throw new ProtocolException("Bytes sobrantes en SHAPE_DELETE");
        return new ShapeDeleteMessage(name);
    }

    private static StrokeResult ParseStrokeResult(byte value) => value <= (byte)StrokeResult.TimedOut
        ? (StrokeResult)value
        : throw new ProtocolException($"Resultado de trazo inválido: {value}");

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

    private static SystemGestureCommand ParseSystemGesture(byte value) => value <= (byte)SystemGestureCommand.AppSwitchEnd
        ? (SystemGestureCommand)value
        : throw new ProtocolException($"Gesto de sistema inválido: {value}");

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
