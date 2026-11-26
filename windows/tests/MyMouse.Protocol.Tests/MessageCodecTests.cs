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
    [InlineData("02 05 00")] // MOVE truncado
    [InlineData("07 78")] // ZOOM truncado
    [InlineData("06 02 00 7A")] // GESTURE truncado
    [InlineData("06 01 00 FF")] // UTF-8 inválido
    [InlineData("86 03")] // estado inválido
    [InlineData("87 02 00 00 01")] // GESTURE_LIST con una entrada que no llega
    [InlineData("87 03 00 00 00 99")] // bytes sobrantes
    [InlineData("03 03 01")] // botón inválido
    [InlineData("03 00 02")] // booleano inválido
    public async Task Read_InvalidInput_Throws(string hex)
    {
        var reader = new MessageReader(new MemoryStream(Hex(hex)));
        await Assert.ThrowsAsync<ProtocolException>(async () => await reader.ReadAsync());
    }

    private static byte[] Hex(string hex) => Convert.FromHexString(hex.Replace(" ", ""));
}
