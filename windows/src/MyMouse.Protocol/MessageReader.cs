namespace MyMouse.Protocol;

/// <summary>Lee mensajes completos de un flujo de bytes.</summary>
public sealed class MessageReader(Stream stream)
{
    private readonly byte[] _buffer = new byte[8];

    /// <summary>
    /// Lee el siguiente mensaje. Devuelve <c>null</c> si el flujo termina limpiamente entre mensajes.
    /// Lanza <see cref="ProtocolException"/> si el flujo termina a mitad de un mensaje o el contenido es inválido.
    /// </summary>
    public async ValueTask<Message?> ReadAsync(CancellationToken cancellationToken = default)
    {
        var read = await stream.ReadAsync(_buffer.AsMemory(0, 1), cancellationToken).ConfigureAwait(false);
        if (read == 0)
            return null;

        var type = _buffer[0];
        var size = MessageCodec.PayloadSize(type);
        if (size > 0)
        {
            try
            {
                await stream.ReadExactlyAsync(_buffer.AsMemory(1, size), cancellationToken).ConfigureAwait(false);
            }
            catch (EndOfStreamException)
            {
                throw new ProtocolException($"Flujo terminado a mitad del mensaje 0x{type:X2}");
            }
        }

        return MessageCodec.Decode(type, _buffer.AsSpan(1, size));
    }
}
