using System.Windows;
using System.Windows.Controls;
using MyMouse.HostCore.Toolbox;

namespace MyMouse.Host.Ui;

/// <summary>
/// La fila de una forma, compartida por la tarjeta Gestos de la toolbox y la lista del editor (docs/spec-v4.5-estilo.md §2.4):
/// alineada a la izquierda, en tres columnas (baldosa con la forma, texto con el chip de la acción y, a la derecha, la chincheta y
/// un "›" al pasar el ratón). 52 px de alto, hover de cristal con radio 8 y 2 px de hueco, sin separadores.
/// </summary>
internal static class GestureRows
{
    public const double RowHeight = 52;
    public const double TileSize = 40;

    /// <summary>Baldosa de 40 × 40 con radio 8 y cristal elevado, con la forma de 24 px, trazo de 2 px y el inicio en acento.</summary>
    public static Border Tile(HostApp app, string gesture, bool dim)
    {
        var thumb = new ShapeThumb(app.ShapePoints(gesture), 24)
        {
            Stroke = dim ? Theme.Dim : Theme.Text,
            StartBrush = Theme.Accent,
            Thickness = 2,
            Padding = 2,
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Center,
        };
        return new Border
        {
            Width = TileSize,
            Height = TileSize,
            CornerRadius = new CornerRadius(Theme.RowRadius),
            Background = Theme.Hover,
            Child = thumb,
            VerticalAlignment = VerticalAlignment.Center,
        };
    }

    public static Button Row(HostApp app, GestureRow row, Action click, bool selected = false)
    {
        var dim = !row.HasAction;

        var title = Theme.Label(row.Title, 13, dim ? Theme.Dim : Theme.Text, FontWeights.SemiBold);
        var chipLine = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 2, 0, 0) };
        if (row.HasAction)
        {
            var info = ActionChip.Format(row.Action);
            var chip = Theme.Chip(info.Text, mono: true);
            if (info.Tooltip is not null) chip.ToolTip = info.Tooltip;
            chipLine.Children.Add(chip);
            if (row.OnlyInSomeApps)
            {
                var some = Theme.Label(Loc.T("gr_some_apps"), 11, Theme.Faint);
                some.Margin = new Thickness(8, 0, 0, 0);
                some.VerticalAlignment = VerticalAlignment.Center;
                chipLine.Children.Add(some);
            }
        }
        else
        {
            chipLine.Children.Add(Theme.Chip(Loc.T("no_action"), Theme.Amber, mono: true));
        }
        var texts = new StackPanel { VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(10, 0, 8, 0) };
        texts.Children.Add(title);
        texts.Children.Add(chipLine);

        var right = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center };
        if (row.Pinned)
        {
            var pin = Theme.Glyph("", 12, Theme.Accent);
            pin.ToolTip = Loc.T("gr_pinned");
            pin.Margin = new Thickness(0, 0, 6, 0);
            right.Children.Add(pin);
        }
        if (!row.HasAction && row.IsCustom)
        {
            // Solo visual: la fila entera abre el editor con la forma seleccionada.
            right.Children.Add(Theme.Label(Loc.T("assign"), 12, Theme.Accent, FontWeights.SemiBold));
        }
        var chevron = Theme.Glyph("", 11, Theme.Faint);
        chevron.Margin = new Thickness(6, 0, 0, 0);
        chevron.Visibility = Visibility.Hidden;
        right.Children.Add(chevron);

        var grid = new Grid();
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        var tile = Tile(app, row.Gesture, dim);
        Grid.SetColumn(texts, 1);
        Grid.SetColumn(right, 2);
        grid.Children.Add(tile);
        grid.Children.Add(texts);
        grid.Children.Add(right);

        var button = Theme.FlatButton(grid, selected ? Theme.Selected : Theme.Transparent, padding: new Thickness(6, 0, 8, 0));
        button.Height = RowHeight;
        button.HorizontalContentAlignment = HorizontalAlignment.Stretch;
        button.Margin = new Thickness(0, 0, 0, 2);
        button.Click += (_, _) => click();
        button.MouseEnter += (_, _) => chevron.Visibility = Visibility.Visible;
        button.MouseLeave += (_, _) => chevron.Visibility = Visibility.Hidden;
        return button;
    }

    /// <summary>Cabecera de sección (Fijadas, "Sin acción · 5"). Con <paramref name="toggle"/> se puede plegar (flecha a la derecha).</summary>
    public static UIElement SectionHeader(string title, bool? open = null, Action? toggle = null)
    {
        var label = Theme.Label(title, 11, Theme.Dim, FontWeights.SemiBold);
        label.VerticalAlignment = VerticalAlignment.Center;
        if (toggle is null) return new Border { Child = label, Margin = new Thickness(6, 8, 0, 4) };

        var row = new DockPanel { LastChildFill = true };
        var arrow = Theme.Glyph(open == true ? "" : "", 10, Theme.Dim);
        arrow.Margin = new Thickness(0, 0, 6, 0);
        DockPanel.SetDock(arrow, Dock.Left);
        row.Children.Add(arrow);
        row.Children.Add(label);
        var button = Theme.FlatButton(row, Theme.Transparent, padding: new Thickness(6, 4, 6, 4));
        button.HorizontalContentAlignment = HorizontalAlignment.Stretch;
        button.Margin = new Thickness(0, 4, 0, 2);
        button.Click += (_, _) => toggle();
        return button;
    }
}
