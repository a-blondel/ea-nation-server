package eu.eahub.enums.nfs;

import lombok.Getter;

/**
 * Car models available in Need For Speed: Carbon - Own the City (PSP).
 */
@Getter
public enum CarbonCar {
    ECLIPSE_GT(14, "ECLIPSE GT"),
    LANCER(15, "LANCER"),
    GOLF_GTI(16, "GOLF GTI"),
    CORVETTE(17, "CORVETTE"),
    COBALT_SS(18, "COBALT SS"),
    CARRERA_GT(19, "CARRERA GT"),
    GALLARDO(20, "GALLARDO"),
    FORD_GT(21, "FORD GT"),
    MUSTANG_GT(22, "MUSTANG GT"),
    WRX_STI(23, "WRX STI"),
    TT_3_2(24, "TT 3.2"),
    RX_8(25, "RX-8"),
    CARRERA_S(26, "911 CARRERA S"),
    MAZDASPEED_3(27, "MAZDASPEED 3"),
    SUPRA(28, "SUPRA"),
    SOLSTICE(29, "SOLSTICE"),
    RX_7(30, "RX-7"),
    SKYLINE(31, "SKYLINE"),
    SILVIA_240SX(32, "240 SX"),
    MR2(33, "MR2"),
    Z350(34, "350Z"),
    GTO(35, "GTO"),
    MUSTANG_1967(36, "1967 MUSTANG"),
    C300_SRT8(37, "300C SRT8"),
    MURCIELAGO(38, "MURCIÉLAGO"),
    DB9(39, "DB9"),
    ELISE(40, "ELISE"),
    SL65_AMG(41, "SL65 AMG"),
    FIREBIRD(42, "FIREBIRD");

    private final int id;
    private final String model;

    CarbonCar(int id, String model) {
        this.id = id;
        this.model = model;
    }

    public static CarbonCar fromId(int id) {
        for (CarbonCar car : values()) {
            if (car.id == id) {
                return car;
            }
        }
        return null;
    }

    public static String getModelName(Integer carId) {
        if (carId == null) return "UNKNOWN";
        CarbonCar car = fromId(carId);
        return car != null ? car.model : "UNKNOWN";
    }
}
