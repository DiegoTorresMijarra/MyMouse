using System.Text.Json;

namespace MyMouse.HostCore.Toolbox;

/// <summary>Tarjetas de la toolbox (docs/spec-v4-toolbox.md §2.3).</summary>
public enum ToolboxCard
{
    /// <summary>Estado y botones. Fija: no se puede quitar ni mover.</summary>
    Options,
    Music,
    Gestures,

    /// <summary>Widgets del panel del móvil: oculta por defecto.</summary>
    Widgets,
}

/// <summary>
/// Qué tarjetas se ven y en qué orden, y si se muestra el gesto en vivo. Se guarda en <c>toolbox.json</c>.
/// Inmutable: cada cambio devuelve un diseño nuevo. <see cref="ToolboxCard.Options"/> siempre va la primera y visible.
/// </summary>
public sealed class ToolboxLayout
{
    private readonly (ToolboxCard Card, bool Visible)[] _entries;

    private ToolboxLayout(IEnumerable<(ToolboxCard Card, bool Visible)> entries, bool showLiveGesture)
    {
        _entries = Normalize(entries);
        ShowLiveGesture = showLiveGesture;
    }

    public static ToolboxLayout Default { get; } = new(
        [(ToolboxCard.Options, true), (ToolboxCard.Music, true), (ToolboxCard.Gestures, true), (ToolboxCard.Widgets, false)],
        showLiveGesture: true);

    /// <summary>"Mostrar el gesto en vivo" en los ajustes de la toolbox.</summary>
    public bool ShowLiveGesture { get; }

    /// <summary>Todas las tarjetas en su orden, con si están visibles (para la ventana de personalizar).</summary>
    public IReadOnlyList<(ToolboxCard Card, bool Visible)> Entries => _entries;

    /// <summary>Las tarjetas visibles, en orden.</summary>
    public IReadOnlyList<ToolboxCard> Visible => [.. _entries.Where(e => e.Visible).Select(e => e.Card)];

    public bool IsVisible(ToolboxCard card) => _entries.First(e => e.Card == card).Visible;

    /// <summary>Marca o desmarca una tarjeta. Las Opciones no se pueden quitar: se ignora.</summary>
    public ToolboxLayout SetVisible(ToolboxCard card, bool visible) =>
        card == ToolboxCard.Options
            ? this
            : new ToolboxLayout(_entries.Select(e => e.Card == card ? (e.Card, visible) : e), ShowLiveGesture);

    /// <summary>Mueve una tarjeta <paramref name="delta"/> puestos (−1 = arriba). Nada pasa por encima de las Opciones.</summary>
    public ToolboxLayout Move(ToolboxCard card, int delta)
    {
        if (card == ToolboxCard.Options) return this;
        var list = _entries.ToList();
        var from = list.FindIndex(e => e.Card == card);
        var to = Math.Clamp(from + delta, 1, list.Count - 1);
        if (to == from) return this;
        var item = list[from];
        list.RemoveAt(from);
        list.Insert(to, item);
        return new ToolboxLayout(list, ShowLiveGesture);
    }

    public ToolboxLayout WithLiveGesture(bool show) => new(_entries, show);

    // ---- JSON ----

    private static readonly JsonSerializerOptions JsonOptions = new() { WriteIndented = true };

    public string ToJson()
    {
        var model = new
        {
            cards = _entries.Select(e => new { id = e.Card.ToString().ToLowerInvariant(), visible = e.Visible }).ToArray(),
            liveGesture = ShowLiveGesture,
        };
        return JsonSerializer.Serialize(model, JsonOptions);
    }

    /// <summary>
    /// Lee <c>toolbox.json</c>. Lo que falta o no se entiende se completa con los valores por defecto: tarjetas que no están
    /// en el fichero se añaden al final con su visibilidad por defecto. Un JSON roto da el diseño por defecto.
    /// </summary>
    public static ToolboxLayout Parse(string json)
    {
        try
        {
            using var doc = JsonDocument.Parse(json);
            var entries = new List<(ToolboxCard, bool)>();
            if (doc.RootElement.TryGetProperty("cards", out var cards) && cards.ValueKind == JsonValueKind.Array)
            {
                foreach (var c in cards.EnumerateArray())
                {
                    if (c.ValueKind != JsonValueKind.Object ||
                        !c.TryGetProperty("id", out var id) || id.ValueKind != JsonValueKind.String ||
                        !Enum.TryParse<ToolboxCard>(id.GetString(), ignoreCase: true, out var card)) continue;
                    var visible = !c.TryGetProperty("visible", out var v) || v.ValueKind != JsonValueKind.False;
                    entries.Add((card, visible));
                }
            }
            var live = !doc.RootElement.TryGetProperty("liveGesture", out var lg) || lg.ValueKind != JsonValueKind.False;
            return new ToolboxLayout(entries, live);
        }
        catch (JsonException)
        {
            return Default;
        }
    }

    public static ToolboxLayout Load(string path)
    {
        try
        {
            return File.Exists(path) ? Parse(File.ReadAllText(path)) : Default;
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            return Default;
        }
    }

    public void Save(string path)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(path))!);
        File.WriteAllText(path, ToJson());
    }

    /// <summary>Sin repeticiones, con todas las tarjetas y las Opciones primero y visibles.</summary>
    private static (ToolboxCard Card, bool Visible)[] Normalize(IEnumerable<(ToolboxCard Card, bool Visible)> entries)
    {
        var result = new List<(ToolboxCard Card, bool Visible)> { (ToolboxCard.Options, true) };
        foreach (var e in entries)
            if (e.Card != ToolboxCard.Options && result.All(r => r.Card != e.Card)) result.Add(e);
        foreach (var card in Enum.GetValues<ToolboxCard>())
            if (result.All(r => r.Card != card)) result.Add((card, card != ToolboxCard.Widgets));
        return [.. result];
    }
}
