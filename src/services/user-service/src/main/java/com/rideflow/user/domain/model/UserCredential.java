package com.rideflow.user.domain.model;

/** User kèm hash mật khẩu; chỉ dùng nội bộ cho đăng nhập, không bao giờ serialize ra ngoài. */
public record UserCredential(User user, String passwordHash) {
}
