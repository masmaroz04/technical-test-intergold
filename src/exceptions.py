class InsufficientFundsError(Exception):
    """ยอดเงินไม่เพียงพอสำหรับซื้อทอง"""
    pass


class InsufficientGoldError(Exception):
    """มีทองไม่เพียงพอสำหรับขาย"""
    pass


class CustomerNotFoundError(Exception):
    """ไม่พบลูกค้าในระบบ"""
    pass
