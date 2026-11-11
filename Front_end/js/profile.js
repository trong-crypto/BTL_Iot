document.getElementById("profileForm")?.addEventListener("submit", event => {
    event.preventDefault();
    alert("Đã lưu thông tin demo.");
});

document.getElementById("passwordForm")?.addEventListener("submit", event => {
    event.preventDefault();
    alert("Đã cập nhật mật khẩu demo.");
});
