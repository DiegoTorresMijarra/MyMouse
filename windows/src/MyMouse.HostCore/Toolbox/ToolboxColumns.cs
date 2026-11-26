namespace MyMouse.HostCore.Toolbox;

/// <summary>Reparto de las tarjetas en dos columnas con la toolbox maximizada (docs/spec-v4.5-estilo.md §2.2).</summary>
public static class ToolboxColumns
{
    public const double DefaultWidth = 960;
    public const double DefaultHeight = 720;
    public const double WorkAreaFraction = 0.85;

    /// <summary>
    /// Opciones y Música a la izquierda, Gestos (la más alta) a la derecha y Widgets debajo de la columna con menos altura.
    /// Si no hay tarjetas en la derecha (Gestos oculta), la que va después de las Opciones pasa a la derecha para no dejarla vacía.
    /// </summary>
    public static (IReadOnlyList<ToolboxCard> Left, IReadOnlyList<ToolboxCard> Right) Split(
        IReadOnlyList<ToolboxCard> visible, Func<ToolboxCard, double> height)
    {
        var left = new List<ToolboxCard>();
        var right = new List<ToolboxCard>();
        foreach (var card in visible)
        {
            switch (card)
            {
                case ToolboxCard.Options or ToolboxCard.Music: left.Add(card); break;
                case ToolboxCard.Gestures: right.Add(card); break;
            }
        }
        if (right.Count == 0 && left.Count > 1)
        {
            right.Add(left[^1]);
            left.RemoveAt(left.Count - 1);
        }
        if (visible.Contains(ToolboxCard.Widgets))
        {
            var l = left.Sum(height);
            var r = right.Sum(height);
            (l <= r ? left : right).Add(ToolboxCard.Widgets);
        }
        return (left, right);
    }

    /// <summary>Tamaño de la ventana maximizada: 960 × 720 o el 85 % del área de trabajo si es menor (en las mismas unidades).</summary>
    public static (double Width, double Height) WindowSize(double workWidth, double workHeight) =>
        (Math.Min(DefaultWidth, workWidth * WorkAreaFraction), Math.Min(DefaultHeight, workHeight * WorkAreaFraction));
}
