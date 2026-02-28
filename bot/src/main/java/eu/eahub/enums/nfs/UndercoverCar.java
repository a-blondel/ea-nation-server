package eu.eahub.enums.nfs;

import lombok.Getter;

/**
 * Car models available in Need For Speed: Undercover (PSP).
 */
@Getter
public enum UndercoverCar {
    LANCER_EVO_IX(11, "LANCER EVO IX"),
    GOLF_GTI(12, "GOLF GTI"),
    CORVETTE(13, "CORVETTE"),
    CARRERA_GT(14, "CARRERA GT"),
    GALLARDO(15, "GALLARDO"),
    FORD_GT(16, "FORD GT"),
    MUSTANG_GT(17, "MUSTANG GT"),
    RX_8(18, "RX-8"),
    MAZDA3_MPS(19, "MAZDA3 MPS"),
    SUPRA(20, "SUPRA"),
    SOLSTICE_GXP(21, "SOLSTICE GXP"),
    RX_7(22, "RX-7"),
    SKYLINE(23, "SKYLINE"),
    SILVIA_240SX(24, "240SX (S13)"),
    Z350(25, "350Z (Z33)"),
    MUSTANG_67(26, "67 MUSTANG"),
    C300_SRT8(27, "300C SRT8"),
    DB9(28, "DB9"),
    ELISE(29, "ELISE"),
    SL65_AMG(30, "SL65 AMG"),
    FIREBIRD(31, "FIREBIRD"),
    ZONDA_F(32, "ZONDA F"),
    GT2(33, "GT2"),
    LANCER(34, "LANCER"),
    MUSTANG(35, "MUSTANG"),
    R8(36, "R8"),
    CAYMAN(37, "CAYMAN"),
    GT2_2(38, "GT2"),
    CHARGER(39, "CHARGER"),
    M3_E92(40, "M3 E92"),
    GALLARDO_2(41, "GALLARDO"),
    GTR(42, "GTR"),
    LANCER_EVO_X(43, "LANCER EVO X"),
    VIPER(44, "VIPER"),
    SRT8(45, "SRT8"),
    GT2_911(46, "911 GT2"),
    Z370(47, "370Z (Z34)");

    private final int id;
    private final String model;

    UndercoverCar(int id, String model) {
        this.id = id;
        this.model = model;
    }

    public static UndercoverCar fromId(int id) {
        for (UndercoverCar car : values()) {
            if (car.id == id) {
                return car;
            }
        }
        return null;
    }

    public static String getModelName(Integer carId) {
        if (carId == null) return "UNKNOWN";
        UndercoverCar car = fromId(carId);
        return car != null ? car.model : "UNKNOWN";
    }
}
