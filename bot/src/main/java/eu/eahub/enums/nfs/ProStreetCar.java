package eu.eahub.enums.nfs;

import lombok.Getter;

/**
 * Car models available in Need For Speed: ProStreet (PSP).
 */
@Getter
public enum ProStreetCar {
    EVOLUTION_IX(14, "EVOLUTION IX"),
    GOLF_GTI(15, "GOLF GTI"),
    CORVETTE_Z06(16, "CORVETTE Z06"),
    COBALT_SS(17, "COBALT SS"),
    FORD_GT(18, "FORD GT"),
    GT_500(19, "GT 500"),
    IMPREZA_WRX_STI(20, "IMPREZA WRX STI"),
    RX_8(21, "RX-8"),
    MAZDASPEED3(22, "MAZDASPEED3"),
    SUPRA(23, "SUPRA"),
    SOLSTICE_GXP(24, "SOLSTICE GXP"),
    RX_7(25, "RX-7"),
    SKYLINE_GT_R(26, "SKYLINE GT-R (R34)"),
    Z350(27, "350Z (Z33)"),
    GT500_ELEANOR(28, "GT500 (Eleanor)"),
    MURCIELAGO_LP640(29, "MURCIÉLAGO LP640"),
    ELISE(30, "ELISE"),
    RS4(31, "RS4"),
    LANCER_EVOLUTION(32, "LANCER EVOLUTION"),
    COROLLA_GTS(33, "COROLLA GTS (AE86)"),
    GOLF_R32(34, "GOLF R32"),
    CIVIC_SI(35, "CIVIC SI"),
    SILVIA_S15(36, "SILVIA (S15)"),
    INTEGRA_TYPE_R(37, "INTEGRA TYPE R"),
    CAMARO_CONCEPT(38, "CAMARO CONCEPT"),
    CHARGER_RT(39, "CHARGER R/T"),
    CHALLENGER(40, "CHALLENGER"),
    NSX(41, "NSX"),
    M3_E92(42, "M3 E92"),
    GT_R_R35(43, "GT-R (R35)"),
    CAYMAN_S(44, "CAYMAN S"),
    TURBO_911(45, "911 TURBO"),
    ZONDA_F(46, "ZONDA F"),
    GT2_911(47, "911 GT2"),
    SKYLINE_GT_R_RACE(48, "SKYLINE GT-R (R34) (RACE)"),
    GT_R_PROTO_RACE(49, "GT-R PROTO (RACE)"),
    EVOLUTION_RACE(50, "EVOLUTION (RACE)"),
    M3_E92_RACE(51, "M3 E92 (RACE)"),
    IMPREZA_WRX_STI_RACE(52, "IMPREZA WRX STI (RACE)"),
    RX_8_RACE(53, "RX-8 (RACE)"),
    CIVIC_SI_RACE(54, "CIVIC SI (RACE)"),
    SILVIA_S15_RACE(55, "SILVIA (S15) (RACE)"),
    COROLLA_GTS_RACE(56, "COROLLA GTS (AE86) (RACE)"),
    CAYMAN_S_RACE(57, "CAYMAN S (RACE)"),
    FORD_GT_RACE(58, "FORD GT (RACE)"),
    MURCIELAGO_LP640_RACE(59, "MURCIÉLAGO LP640 (RACE)"),
    ZONDA_F_RACE(60, "ZONDA F (RACE)"),
    GT2_911_RACE(61, "911 GT2 (RACE)"),
    CAMARO_CONCEPT_RACE(62, "CAMARO CONCEPT (RACE)"),
    GT_500_RACE(63, "GT 500 (RACE)");

    private final int id;
    private final String model;

    ProStreetCar(int id, String model) {
        this.id = id;
        this.model = model;
    }

    public static ProStreetCar fromId(int id) {
        for (ProStreetCar car : values()) {
            if (car.id == id) {
                return car;
            }
        }
        return null;
    }

    public static String getModelName(Integer carId) {
        if (carId == null) return "UNKNOWN";
        ProStreetCar car = fromId(carId);
        return car != null ? car.model : "UNKNOWN";
    }
}
