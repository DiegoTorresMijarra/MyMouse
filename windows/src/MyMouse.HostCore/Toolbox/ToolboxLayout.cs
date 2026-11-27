using System.Text.Json;
using MyMouse.HostCore.Theming;

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

    private ToolboxLayout(IEnumerable<(ToolboxCard Card, bool Visible)> entries, bool showLiveGesture, bool glass = true, bool maximized = false, bool alwaysShowIcon = true, ThemeChoice? theme = null, long themeChangedAtMs = 0)
    {
        _entries = Normalize(entries);
        ShowLiveGesture = showLiveGesture;
        GlassEffect = glass;
        Maximized = maximized;
        AlwaysShowIcon = alwaysShowIcon;
        Theme = (theme ?? new ThemeChoice()).Normalized();
        ThemeChangedAtMs = Math.Max(0, themeChangedAtMs);
    }

    public static ToolboxLayout Default { get; } = new(
        [(ToolboxCard.Options, true), (ToolboxCard.Music, true), (ToolboxCard.Gestures, true), (ToolboxCard.Widgets, false)],
        showLiveGesture: true);

    /// <summary>"Mostrar el gesto en vivo" en los ajustes de la toolbox.</summary>
    public bool ShowLiveGesture { get; }

    /// <summary>
    /// "Efecto cristal": tarjetas translúcidas sobre el universo (v4.7; antes, Acrylic del sistema). Desactivado, las tarjetas son
    /// sólidas (del color de la base del tema, v4.8) sobre el mismo universo.
    /// </summary>
    public bool GlassEffect { get; }

    /// <summary>
    /// "Mostrar siempre el icono en la barra de tareas" (v4.7 §3.4): saca el icono de la bandeja oculta en Windows 11. Activado por
    /// defecto, así que la primera vez que arranca la v4.7 se activa solo; si el usuario lo quita, se guarda y no se vuelve a poner.
    /// </summary>
    public bool AlwaysShowIcon { get; }

    /// <summary>
    /// Tema, acento y estrellas de este PC (docs/spec-v4.8-temas.md). Se guarda en <c>toolbox.json</c>. Desde la v6.1 el tema, el
    /// acento y las estrellas se sincronizan con el móvil por USB (la animación no).
    /// Un <c>toolbox.json</c> sin tema (anterior a la v4.8) da Universo, con la paleta sobria.
    /// </summary>
    public ThemeChoice Theme { get; }

    /// <summary>Cuándo se eligió el tema por última vez (ms desde 1970, UTC); 0 = nunca (o anterior a la v6.1).</summary>
    public long ThemeChangedAtMs { get; }

    /// <summary>La toolbox se quedó maximizada (ventana de dos columnas) y se abre así.</summary>
    public bool Maximized { get; }

    /// <summary>Todas las tarjetas en su orden, con si están visibles (para la ventana de personalizar).</summary>
    public IReadOnlyList<(ToolboxCard Card, bool Visible)> Entries => _entries;

    /// <summary>Las tarjetas visibles, en orden.</summary>
    public IReadOnlyList<ToolboxCard> Visible => [.. _entries.Where(e => e.Visible).Select(e => e.Card)];

    public bool IsVisible(ToolboxCard card) => _entries.First(e => e.Card == card).Visible;

    /// <summary>Marca o desmarca una tarjeta. Las Opciones no se pueden quitar: se ignora.</summary>
    public ToolboxLayout SetVisible(ToolboxCard card, bool visible) =>
        card == ToolboxCard.Options
            ? this
            : new ToolboxLayout(_entries.Select(e => e.Card == card ? (e.Card, visible) : e), ShowLiveGesture, GlassEffect, Maximized, AlwaysShowIcon, Theme, ThemeChangedAtMs);

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
        return new ToolboxLayout(list, ShowLiveGesture, GlassEffect, Maximized, AlwaysShowIcon, Theme, ThemeChangedAtMs);
    }

    public ToolboxLayout WithLiveGesture(bool show) => new(_entries, show, GlassEffect, Maximized, AlwaysShowIcon, Theme, ThemeChangedAtMs);

    public ToolboxLayout WithGlass(bool glass) => new(_entries, ShowLiveGesture, glass, Maximized, AlwaysShowIcon, Theme, ThemeChangedAtMs);

    public ToolboxLayout WithMaximized(bool maximized) => new(_entries, ShowLiveGesture, GlassEffect, maximized, AlwaysShowIcon, Theme, ThemeChangedAtMs);

    public ToolboxLayout WithAlwaysShowIcon(bool value) => new(_entries, ShowLiveGesture, GlassEffect, Maximized, value, Theme, ThemeChangedAtMs);

    /// <summary>Otro tema, elegido en <paramref name="changedAtMs"/> (ms desde 1970, UTC; protocolo v8: gana el cambio más reciente).</summary>
    public ToolboxLayout WithTheme(ThemeChoice theme, long changedAtMs) => new(_entries, ShowLiveGesture, GlassEffect, Maximized, AlwaysShowIcon, theme, changedAtMs);

    // ---- JSON ----

    private static readonly JsonSerializerOptions JsonOptions = new() { WriteIndented = true };

    public string ToJson()
    {
        var model = new
        {
            cards = _entries.Select(e => new { id = e.Card.ToString().ToLowerInvariant(), visible = e.Visible }).ToArray(),
            liveGesture = ShowLiveGesture,
            glass = GlassEffect,
            maximized = Maximized,
            alwaysShowIcon = AlwaysShowIcon,
            theme = new { id = Theme.ThemeId, accent = Theme.AccentId, stars = Theme.Stars, twinkle = Theme.Twinkle, changedAt = ThemeChangedAtMs },
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
            var glass = !doc.RootElement.TryGetProperty("glass", out var g) || g.ValueKind != JsonValueKind.False;
            var maximized = doc.RootElement.TryGetProperty("maximized", out var m) && m.ValueKind == JsonValueKind.True;
            var always = !doc.RootElement.TryGetProperty("alwaysShowIcon", out var a) || a.ValueKind != JsonValueKind.False;
            var theme = new ThemeChoice();
            long themeChangedAt = 0;
            if (doc.RootElement.TryGetProperty("theme", out var th) && th.ValueKind == JsonValueKind.Object)
            {
                theme = new ThemeChoice(
                    th.TryGetProperty("id", out var tid) && tid.ValueKind == JsonValueKind.String ? tid.GetString()! : ThemeCatalog.DefaultId,
                    th.TryGetProperty("accent", out var tac) && tac.ValueKind == JsonValueKind.String ? tac.GetString()! : ThemeCatalog.AccentAuto,
                    !th.TryGetProperty("stars", out var tst) || tst.ValueKind != JsonValueKind.False,
                    !th.TryGetProperty("twinkle", out var ttw) || ttw.ValueKind != JsonValueKind.False);
                if (th.TryGetProperty("changedAt", out var tca) && tca.ValueKind == JsonValueKind.Number && tca.TryGetInt64(out var ms)) themeChangedAt = ms;
            }
            return new ToolboxLayout(entries, live, glass, maximized, always, theme, themeChangedAt);
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
