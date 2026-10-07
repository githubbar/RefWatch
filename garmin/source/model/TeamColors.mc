import Toybox.Lang;

module TeamColors {
    const VALUES = [0xFF0000, 0x0055FF, 0xFFFFFF, 0x000000, 0xFFFF00,
                    0x00AA00, 0xFF8800, 0x8800CC, 0x66CCFF, 0x888888] as Array<Number>;

    function nameId(color as Number) as ResourceId {
        var names = [Rez.Strings.Red, Rez.Strings.Blue, Rez.Strings.White, Rez.Strings.Black,
                     Rez.Strings.Yellow, Rez.Strings.Green, Rez.Strings.Orange, Rez.Strings.Purple,
                     Rez.Strings.SkyBlue, Rez.Strings.Grey] as Array<ResourceId>;
        var index = VALUES.indexOf(color);
        return index < 0 ? Rez.Strings.Custom : names[index];
    }
}
