using MyMouse.HostCore.Gestures;
using MyMouse.HostCore.Shapes;

namespace MyMouse.HostCore.Toolbox;

/// <summary>Una fila de la tarjeta Gestos (y de la lista del editor).</summary>
/// <param name="Gesture">Nombre de la forma (<c>circle</c>, <c>custom:firma</c>).</param>
/// <param name="Title">Texto de ayuda o, si no hay, la acción en lenguaje humano; para formas sin acción, su etiqueta.</param>
/// <param name="Action">La acción mostrada (la general o la primera de las reglas por app); null si no tiene.</param>
/// <param name="Pinned">Fijada en la chuleta del móvil.</param>
/// <param name="OnlyInSomeApps">Solo tiene reglas para algunas apps.</param>
/// <param name="IsCustom">Forma propia del móvil (<c>custom:</c>).</param>
public sealed record GestureRow(string Gesture, string Title, GestureAction? Action, bool Pinned, bool OnlyInSomeApps, bool IsCustom)
{
    public bool HasAction => Action is not null;
}

/// <summary>Las filas agrupadas en las secciones de la tarjeta Gestos.</summary>
public sealed record GestureSections(IReadOnlyList<GestureRow> Pinned, IReadOnlyList<GestureRow> Assigned, IReadOnlyList<GestureRow> Unassigned)
{
    public bool IsEmpty => Pinned.Count == 0 && Assigned.Count == 0 && Unassigned.Count == 0;

    /// <summary>Título de la sección plegada: "Sin acción · 5".</summary>
    public string UnassignedTitle => $"Sin acción · {Unassigned.Count}";

    /// <summary>
    /// Agrupa las formas (docs/spec-v4.5-estilo.md §2.4): "Fijadas" (con acción y chincheta), "Asignadas" (el resto de las que
    /// tienen acción, en el orden del fichero) y "Sin acción" (primero las propias del móvil y después las del catálogo).
    /// </summary>
    public static GestureSections Build(GestureConfig config, IEnumerable<string> customShapeNames)
    {
        var pinned = new List<GestureRow>();
        var assigned = new List<GestureRow>();
        foreach (var gesture in config.Bindings.Select(b => b.Gesture).Distinct(StringComparer.Ordinal))
        {
            var row = RowFor(config, gesture);
            (row.Pinned ? pinned : assigned).Add(row);
        }

        var unassigned = new List<GestureRow>();
        foreach (var name in customShapeNames.Distinct(StringComparer.Ordinal).Where(n => !config.HasAction(n)))
            unassigned.Add(Empty(name, isCustom: true));
        foreach (var shape in ShapeCatalog.All.Where(s => !config.HasAction(s.Id)))
            unassigned.Add(Empty(shape.Id, isCustom: false));
        return new GestureSections(pinned, assigned, unassigned);
    }

    /// <summary>Filtra por texto (título, nombre de la forma o acción), sin distinguir mayúsculas. Vacío: todo.</summary>
    public GestureSections Filter(string? query)
    {
        if (string.IsNullOrWhiteSpace(query)) return this;
        var q = query.Trim();
        bool Match(GestureRow r) =>
            r.Title.Contains(q, StringComparison.CurrentCultureIgnoreCase) ||
            r.Gesture.Contains(q, StringComparison.OrdinalIgnoreCase) ||
            ActionChip.Format(r.Action).Text.Contains(q, StringComparison.CurrentCultureIgnoreCase);
        return new GestureSections([.. Pinned.Where(Match)], [.. Assigned.Where(Match)], [.. Unassigned.Where(Match)]);
    }

    /// <summary>La fila de una forma concreta (con o sin acción), para la lista del editor.</summary>
    public static GestureRow RowFor(GestureConfig config, string gesture)
    {
        var (general, appRules) = config.RulesOf(gesture);
        var binding = general ?? appRules.FirstOrDefault();
        var custom = gesture.StartsWith(ShapeCatalog.CustomPrefix, StringComparison.Ordinal);
        if (binding is null) return Empty(gesture, custom);
        return new GestureRow(gesture, binding.HelpText, binding.Action,
            config.Bindings.Any(b => b.Gesture == gesture && b.Pinned), OnlyInSomeApps: general is null, IsCustom: custom);
    }

    private static GestureRow Empty(string name, bool isCustom) =>
        new(name, ShapeCatalog.Label(name), null, false, false, isCustom);
}
