(() => {
    "use strict";

    window.renderTablePagination = (container, { page, pageSize, total, onPageChange, onPageSizeChange }) => {
        const totalPages = Math.max(1, Math.ceil(total / pageSize));
        const sizeLabel = document.createElement("label");
        sizeLabel.className = "pagination-size-label";
        const labelText = document.createElement("span");
        labelText.textContent = "Số dòng / trang:";
        const select = document.createElement("select");
        select.className = "pagination-size-select";
        [5, 10].forEach(size => {
            const option = document.createElement("option");
            option.value = String(size);
            option.textContent = String(size);
            option.selected = size === pageSize;
            select.append(option);
        });
        select.addEventListener("change", () => onPageSizeChange(Number(select.value)));
        sizeLabel.append(labelText, select);

        const buttons = document.createElement("div");
        buttons.className = "pagination-buttons";
        const addButton = (target, text, label, disabled = false) => {
            const button = document.createElement("button");
            button.type = "button";
            button.className = "pagination-button";
            button.textContent = text;
            button.setAttribute("aria-label", label);
            button.disabled = disabled;
            if (target === page && text === String(page)) {
                button.classList.add("active");
                button.setAttribute("aria-current", "page");
            }
            button.addEventListener("click", () => onPageChange(target));
            buttons.append(button);
        };

        addButton(page - 1, "‹", "Trang trước", page === 1);
        const firstPage = Math.min(Math.max(1, page - 1), Math.max(1, totalPages - 3));
        for (let number = firstPage; number <= Math.min(firstPage + 3, totalPages); number += 1) {
            addButton(number, String(number), `Trang ${number}`);
        }
        addButton(page + 1, "›", "Trang sau", page === totalPages);
        container.replaceChildren(sizeLabel, buttons);
    };
})();
