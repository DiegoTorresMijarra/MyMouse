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
    [InlineData("06 00 00 00")] // GESTURE no existe en la versión 1
    [InlineData("FF")]
    [InlineData("02 05 00")] // MOVE truncado
    [InlineData("03 03 01")] // botón inválido
    [InlineData("03 00 02")] // booleano inválido
    public async Task Read_InvalidInput_Throws(string hex)
    {
        var reader = new MessageReader(new MemoryStream(Hex(hex)));
        await Assert.ThrowsAsync<ProtocolException>(async () => await reader.ReadAsync());
    }

    private static byte[] Hex(string hex) => Convert.FromHexString(hex.Replace(" ", ""));
}
