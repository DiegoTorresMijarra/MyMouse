using System.Buffers.Binary;

namespace MyMouse.Protocol;

/// <summary>Lee mensajes completos de un flujo de bytes.</summary>
public sealed class MessageReader(Stream stream)
{
    private readonly byte[] _small = new byte[8];

    /// <summary>
    /// Lee el siguiente mensaje. Devuelve <c>null</c> si el flujo termina limpiamente entre mensajes.
    /// Lanza <see cref="ProtocolException"/> si el flujo termina a mitad de un mensaje o el contenido es inválido.
    /// </summary>
    public async ValueTask<Message?> ReadAsync(CancellationToken cancellationToken = default)
    {
        var read = await stream.ReadAsync(_small.AsMemory(0, 1), cancellationToken).ConfigureAwait(false);
        if (read == 0)
            return null;

        var type = _small[0];
        var size = MessageCodec.PayloadSize(type);
        if (size is { } fixedSize)
        {
            await ReadExactly(_small.AsMemory(0, fixedSize), type, cancellationToken).ConfigureAwait(false);
            return MessageCodec.Decode(type, _small.AsSpan(0, fixedSize));
        }

        await ReadExactly(_small.AsMemory(0, 2), type, cancellationToken).ConfigureAwait(false);
        var length = BinaryPrimitives.ReadUInt16LittleEndian(_small);
        var payload = new byte[length];
        await ReadExactly(payload, type, cancellationToken).ConfigureAwait(false);
        return MessageCodec.Decode(type, payload);
    }

    private async ValueTask ReadExactly(Memory<byte> buffer, byte type, CancellationToken cancellationToken)
    {
        if (buffer.Length == 0) return;
        try
        {
            await stream.ReadExactlyAsync(buffer, cancellationToken).ConfigureAwait(false);
        }
        catch (EndOfStreamException)
        {
            throw new ProtocolException($"Flujo terminado a mitad del mensaje 0x{type:X2}");
        }
    }
}
