using System.Net.Http.Headers;
using System.Security.Cryptography;

namespace MyMouse.HostCore.Updates;

/// <summary>Descarga la lista de releases y los instaladores. El <see cref="HttpClient"/> se inyecta para poder probarlo.</summary>
public sealed class UpdateChecker(HttpClient http)
{
    /// <summary>Un cliente con la cabecera <c>User-Agent: MyMouse-Updater</c> (la API de GitHub la exige) y un tiempo de espera razonable.</summary>
    public static HttpClient CreateClient()
    {
        var client = new HttpClient { Timeout = TimeSpan.FromSeconds(30) };
        Configure(client);
        return client;
    }

    public static void Configure(HttpClient client)
    {
        client.DefaultRequestHeaders.UserAgent.Clear();
        client.DefaultRequestHeaders.UserAgent.Add(new ProductInfoHeaderValue(Releases.UserAgent, "1"));
        client.DefaultRequestHeaders.Accept.Clear();
        client.DefaultRequestHeaders.Accept.Add(new MediaTypeWithQualityHeaderValue("application/vnd.github+json"));
    }

    /// <summary>Las últimas releases válidas. Lanza <see cref="HttpRequestException"/>, <see cref="FormatException"/> o <see cref="OperationCanceledException"/> si falla.</summary>
    public async Task<IReadOnlyList<ReleaseInfo>> FetchAsync(CancellationToken cancellationToken = default)
    {
        using var response = await http.GetAsync(Releases.ListUrl, cancellationToken).ConfigureAwait(false);
        response.EnsureSuccessStatusCode();
        var json = await response.Content.ReadAsStringAsync(cancellationToken).ConfigureAwait(false);
        return Releases.Parse(json);
    }

    /// <summary>
    /// Descarga <paramref name="asset"/> a <paramref name="destination"/> (primero a un <c>.part</c>) informando del progreso (0–1). Comprueba que el tamaño
    /// coincide con el de la API y, si la API dio el SHA-256, que también coincide; si no, borra lo descargado y lanza <see cref="InvalidDataException"/>.
    /// </summary>
    public async Task DownloadAsync(ReleaseAsset asset, string destination, IProgress<double>? progress, CancellationToken cancellationToken = default)
    {
        if (!Releases.IsTrustedDownloadUrl(asset.DownloadUrl))
            throw new InvalidDataException("La descarga no es de GitHub por HTTPS");

        var partial = destination + ".part";
        try
        {
            using var response = await http.GetAsync(asset.DownloadUrl, HttpCompletionOption.ResponseHeadersRead, cancellationToken).ConfigureAwait(false);
            response.EnsureSuccessStatusCode();
            var total = response.Content.Headers.ContentLength ?? asset.Size;

            using (var sha = SHA256.Create())
            {
                await using var input = await response.Content.ReadAsStreamAsync(cancellationToken).ConfigureAwait(false);
                await using var output = new FileStream(partial, FileMode.Create, FileAccess.Write, FileShare.None, 81920, useAsync: true);
                var buffer = new byte[81920];
                long done = 0;
                int read;
                while ((read = await input.ReadAsync(buffer, cancellationToken).ConfigureAwait(false)) > 0)
                {
                    await output.WriteAsync(buffer.AsMemory(0, read), cancellationToken).ConfigureAwait(false);
                    sha.TransformBlock(buffer, 0, read, null, 0);
                    done += read;
                    if (total > 0) progress?.Report(Math.Min(1.0, (double)done / total));
                }
                sha.TransformFinalBlock([], 0, 0);

                if (asset.Size > 0 && done != asset.Size)
                    throw new InvalidDataException($"El tamaño descargado ({done} bytes) no coincide con el de la release ({asset.Size})");
                if (asset.Sha256 is { } expected && !Convert.ToHexString(sha.Hash!).Equals(expected, StringComparison.OrdinalIgnoreCase))
                    throw new InvalidDataException("La suma SHA-256 del instalador no coincide con la de la release");
            }

            File.Move(partial, destination, overwrite: true);
            progress?.Report(1.0);
        }
        catch
        {
            try { File.Delete(partial); } catch (IOException) { }
            throw;
        }
    }
}
