(() => {
    "use strict";

    const copyIcon = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><rect x="8" y="8" width="12" height="12" rx="2"/><path d="M16 8V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h3"/></svg>';
    const checkIcon = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="m5 12 4 4L19 6"/></svg>';
    let notice;
    let noticeTimer;

    function notify(message) {
        if (!notice) {
            notice = document.createElement("p");
            notice.className = "timestamp-copy-notice";
            notice.setAttribute("role", "status");
            document.body.append(notice);
        }
        clearTimeout(noticeTimer);
        notice.textContent = message;
        notice.hidden = false;
        noticeTimer = setTimeout(() => { notice.hidden = true; }, 3000);
    }

    async function copyText(text) {
        try {
            if (navigator.clipboard?.writeText) {
                await navigator.clipboard.writeText(text);
                return;
            }
        } catch {
            // Some browsers deny clipboard access; try the local selection fallback.
        }
        const focused = document.activeElement;
        const input = document.createElement("textarea");
        input.value = text;
        input.className = "timestamp-copy-buffer";
        input.setAttribute("readonly", "");
        document.body.append(input);
        try {
            input.select();
            if (!document.execCommand("copy")) throw new Error("Copy unavailable");
        } finally {
            input.remove();
            focused?.focus({ preventScroll: true });
        }
    }

    window.createTimestampCell = (createdAt, recordLabel) => {
        const cell = document.createElement("td");
        const wrapper = document.createElement("span");
        wrapper.className = "timestamp-with-copy";
        const time = document.createElement("time");
        time.dateTime = createdAt;
        const text = createdAt.slice(0, 19).replace("T", " ");
        time.textContent = text;
        const button = document.createElement("button");
        button.type = "button";
        button.className = "timestamp-copy-button";
        const label = `Sao chép thời gian ${text} (${recordLabel})`;
        button.setAttribute("aria-label", label);
        button.title = "Sao chép thời gian";
        button.innerHTML = copyIcon;
        let resetTimer;
        button.addEventListener("click", async () => {
            button.disabled = true;
            clearTimeout(resetTimer);
            try {
                await copyText(text);
                button.innerHTML = checkIcon;
                button.classList.add("is-copied");
                button.title = "Đã sao chép";
                notify(`Đã sao chép: ${text}`);
            } catch {
                button.innerHTML = copyIcon;
                button.classList.remove("is-copied");
                button.title = "Sao chép thời gian";
                notify("Không sao chép được. Hãy bôi đen thời gian và nhấn Ctrl+C.");
            } finally {
                button.disabled = false;
                resetTimer = setTimeout(() => {
                    button.innerHTML = copyIcon;
                    button.classList.remove("is-copied");
                    button.title = "Sao chép thời gian";
                }, 2000);
            }
        });
        wrapper.append(time, button);
        cell.append(wrapper);
        return cell;
    };
})();
