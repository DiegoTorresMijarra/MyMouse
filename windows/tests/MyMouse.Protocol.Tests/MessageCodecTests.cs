using MyMouse.Protocol;

namespace MyMouse.Protocol.Tests;

/// <summary>Vectores de protocol/PROTOCOL.md. Si cambian allí, deben cambiar aquí (y en Kotlin).</summary>
public class MessageCodecTests
{
    public static TheoryData<Message, string> Vectors => new()
    {
        { new HelloMessage(1), "01 01" },
        { new HelloAckMessage(1), "81 01" },
        { new MoveMessage(5, -3), "02 05 00 FD FF" },
        { new MoveMessage(-300, 1000), "02 D4 FE E8 03" },
        { new ButtonMessage(MouseButton.Left, true), "03 00 01" },
        { new ButtonMessage(MouseButton.Right, false), "03 01 00" },
        { new ScrollMessage(120, -40), "04 78 00 D8 FF" },
        { new ZoomMessage(-120), "07 88 FF" },
        { new GestureMessage("z"), "06 01 00 7A" },
        { new GestureResultMessage(GestureStatus.Unassigned), "86 01" },
        { new GestureListMessage("", [new GestureListEntry("z", "Deshacer", true)]), "87 0E 00 00 01 01 01 7A 08 44 65 73 68 61 63 65 72" },
        { new WidgetPressMessage(3), "08 03" },
        { new VolumeSetMessage(55), "09 37" },
        { new MediaCommandMessage(MediaCommand.Next), "0A 01" },
        { new VolumeMuteMessage(true), "0B 01" },
        { new SystemGestureMessage(SystemGestureCommand.TaskView), "0C 00" },
        { new SystemGestureMessage(SystemGestureCommand.AppSwitchStart), "0C 02" },
        { new SystemGestureMessage(SystemGestureCommand.AppSwitchEnd), "0C 05" },
        { new VolumeStateMessage(40, false), "89 28 00" },
        { new WidgetResultMessage(2, GestureStatus.Executed), "8B 02 00" },
        { new WidgetLayoutMessage([new WidgetLayoutEntry(0, WidgetKind.Volume, "", "")]), "88 05 00 01 00 02 00 00" },
        { new MediaStateMessage(true, "A", "B"), "8A 05 00 01 01 41 01 42" },
        { new ToolboxMessage(true), "0D 01" },
        { new ToolboxMessage(false), "0D 00" },
        { new GestureModeMessage(true, 3000), "0E 01 B8 0B" },
        { new GestureModeMessage(false, 0), "0E 00 00 00" },
        { new StrokePointsMessage([new StrokePoint(0, 0), new StrokePoint(65535, 32768)]), "0F 09 00 02 00 00 00 00 FF FF 00 80" },
        { new StrokeEndMessage(StrokeResult.Recognized), "10 00" },
        { new StrokeEndMessage(StrokeResult.TimedOut), "10 03" },
        { new ShapeInfoMessage("a", [[new StrokePoint(1, 2), new StrokePoint(3, 4)]]), "11 0C 00 01 61 01 02 01 00 02 00 03 00 04 00" },
        { new ShapeListEndMessage(3), "12 03" },
        { new ShapeResultMessage("a", ShapeStatus.Clashes, "z"), "13 05 00 01 61 01 01 7A" },
        { new ShapeResultMessage("a", ShapeStatus.Saved, ""), "13 04 00 01 61 00 00" },
        { new ShapeAddMessage("a", [[new StrokePoint(1, 2), new StrokePoint(3, 4)]], true), "8C 0D 00 01 61 01 02 01 00 02 00 03 00 04 00 01" },
        { new ShapeDeleteMessage("a"), "8D 02 00 01 61" },
        { new AppVersionMessage("5.0"), "14 04 00 03 35 2E 30" },
        { new HostAppVersionMessage("5.0"), "8E 04 00 03 35 2E 30" },
        { new AppVersionMessage("0.0-dev.42"), "14 0B 00 0A 30 2E 30 2D 64 65 76 2E 34 32" },
        { PingMessage.Instance, "05" },
        { PongMessage.Instance, "85" },
    };

    [Theory]
    [MemberData(nameof(Vectors))]
    public void Encode_MatchesSpecVector(Message message, string hex)
    {
        Assert.Equal(Hex(hex), MessageCodec.Encode(message));
    }

    [Theory]
    [MemberData(nameof(Vectors))]
    public async Task Read_MatchesSpecVector(Message expected, string hex)
    {
        var reader = new MessageReader(new MemoryStream(Hex(hex)));
        Assert.Equal(expected, await reader.ReadAsync());
        Assert.Null(await reader.ReadAsync());
    }

    [Fact]
    public async Task Read_SeveralMessagesInOneStream()
    {
        var bytes = Hex("01 01 02 05 00 FD FF 05");
        var reader = new MessageReader(new MemoryStream(bytes));

        Assert.Equal(new HelloMessage(1), await reader.ReadAsync());
        Assert.Equal(new MoveMessage(5, -3), await reader.ReadAsync());
        Assert.Equal(PingMessage.Instance, await reader.ReadAsync());
        Assert.Null(await reader.ReadAsync());
    }

    [Theory]
    [InlineData("06 00 00")] // GESTURE con nombre vacío
    [InlineData("FF")]
    [InlineData("14 03 00 03 35 2E")] // APP_VERSION truncado
    [InlineData("14 05 00 03 35 2E 30 00")] // bytes sobrantes
    [InlineData("8E 04 00 05 35 2E 30")] // longitud del texto mayor que el payload
    [InlineData("02 05 00")] // MOVE truncado
    [InlineData("07 78")] // ZOOM truncado
    [InlineData("06 02 00 7A")] // GESTURE truncado
    [InlineData("06 01 00 FF")] // UTF-8 inválido
    [InlineData("86 03")] // estado inválido
    [InlineData("87 02 00 00 01")] // GESTURE_LIST con una entrada que no llega
    [InlineData("87 03 00 00 00 99")] // bytes sobrantes
    [InlineData("03 03 01")] // botón inválido
    [InlineData("03 00 02")] // booleano inválido
    [InlineData("0A 03")] // orden multimedia inválida
    [InlineData("0B 02")] // booleano inválido
    [InlineData("0C 06")] // gesto de sistema inválido
    [InlineData("0C")] // SYSTEM_GESTURE truncado
    [InlineData("89 65 00")] // volumen > 100
    [InlineData("8B 02 03")] // estado inválido
    [InlineData("88 05 00 01 00 03 00 00")] // tipo de widget inválido
    [InlineData("88 06 00 01 00 02 00 00 99")] // bytes sobrantes
    [InlineData("88 04 00 01 00 02 00")] // entrada truncada
    [InlineData("8A 04 00 01 01 41 01")] // artista truncado
    [InlineData("8A 05 00 02 01 41 01 42")] // booleano inválido
    [InlineData("08")] // WIDGET_PRESS truncado
    [InlineData("0D 02")] // booleano inválido
    [InlineData("0E 02 00 00")] // booleano inválido
    [InlineData("0F 01 00 00")] // STROKE_POINTS sin puntos
    [InlineData("0F 02 00 01 00")] // STROKE_POINTS truncado
    [InlineData("0F 05 00 02 00 00 00 00")] // dice 2 puntos, trae 1
    [InlineData("10 04")] // resultado de trazo inválido
    [InlineData("11 02 00 01 61")] // SHAPE_INFO sin lista de muestras
    [InlineData("11 0D 00 01 61 01 02 01 00 02 00 03 00 04 00 00")] // bytes sobrantes
    [InlineData("13 05 00 01 61 06 01 7A")] // estado de forma inválido
    [InlineData("8C 0C 00 01 61 01 02 01 00 02 00 03 00 04 00")] // SHAPE_ADD sin force
    [InlineData("8C 0D 00 01 61 01 02 01 00 02 00 03 00 04 00 02")] // force inválido
    [InlineData("8D 02 00 01")] // nombre truncado
    [InlineData("12")] // SHAPE_LIST_END truncado
    public async Task Read_InvalidInput_Throws(string hex)
    {
        var reader = new MessageReader(new MemoryStream(Hex(hex)));
        await Assert.ThrowsAsync<ProtocolException>(async () => await reader.ReadAsync());
    }

    [Fact]
    public void Encode_VolumeSetAbove100_IsKeptAsIs()
    {
        // El recorte a 100 lo hace el host, no el protocolo.
        Assert.Equal(Hex("09 C8"), MessageCodec.Encode(new VolumeSetMessage(200)));
    }

    [Fact]
    public void Encode_WidgetLayout_TruncatesTextWithoutBreakingCharacters()
    {
        var label = new string('é', 200); // 400 bytes UTF-8
        var bytes = MessageCodec.Encode(new WidgetLayoutMessage([new WidgetLayoutEntry(0, WidgetKind.Button, label, "")]));
        var decoded = (WidgetLayoutMessage)MessageCodec.Decode(bytes[0], bytes.AsSpan(3));
        Assert.Equal(new string('é', 127), decoded.Entries[0].Label);
    }

    [Fact]
    public void RoundTrip_ShapeWithAccentsAndManySamples()
    {
        IReadOnlyList<StrokePoint> sample = [.. Enumerable.Range(0, 32).Select(i => new StrokePoint((ushort)(i * 2000), (ushort)(65535 - i * 2000)))];
        var message = new ShapeAddMessage("custom:café", [sample, sample, sample, sample, sample], false);
        var bytes = MessageCodec.Encode(message);
        Assert.Equal(message, MessageCodec.Decode(bytes[0], bytes.AsSpan(3)));
    }

    private static byte[] Hex(string hex) => Convert.FromHexString(hex.Replace(" ", ""));
}
