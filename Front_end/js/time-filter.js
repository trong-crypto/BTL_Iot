(() => {
    "use strict";

    window.parseTimeFilter = value => {
        const text = value.trim();
        const iso = /^(\d{4})-(\d{2})-(\d{2})(?:[ T](\d{2}):(\d{2})(?::(\d{2}))?)?$/.exec(text);
        const legacy = /^(\d{2})\/(\d{2})\/(\d{4})(?:\s*-\s*(\d{2}):(\d{2})(?::(\d{2}))?)?$/.exec(text);
        if (!iso && !legacy) return null;
        const [year, month, day, hour, minute, second] = iso
            ? iso.slice(1) : [legacy[3], legacy[2], legacy[1], ...legacy.slice(4)];
        const h = hour ?? "00", m = minute ?? "00", s = second ?? "00";
        const date = new Date(Number(year), Number(month) - 1, Number(day), Number(h), Number(m), Number(s));
        if (date.getFullYear() !== Number(year) || date.getMonth() !== Number(month) - 1 ||
            date.getDate() !== Number(day) || date.getHours() !== Number(h) ||
            date.getMinutes() !== Number(m) || date.getSeconds() !== Number(s)) return null;
        const prefix = `${year}-${month}-${day}T`;
        // Match only the entered day, minute, or second, including stored fractional seconds.
        return {
            from: `${prefix}${h}:${m}:${s}`,
            to: `${prefix}${hour ?? "23"}:${minute ?? "59"}:${second ?? "59"}.999999`
        };
    };
})();
