package com.cubapincha.job.data;

import com.cubapincha.job.model.AppNotification;
import com.cubapincha.job.model.Application;
import com.cubapincha.job.model.Job;
import com.cubapincha.job.util.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Datos semilla realistas en español para el modo demo.
 * Cobertura nacional: al menos una oferta por cada una de las 16 provincias
 * (15 + Isla de la Juventud), con oficios, periodos de pago y condiciones
 * variados. La Habana ya no es la única provincia representada.
 */
public final class SeedData {
    private SeedData() {}

    public static final String[] CATEGORIES = {
            "Tecnología", "Salud y cuidado", "Gastronomía", "Construcción", "Educación",
            "Turismo", "Servicios", "Comercio", "Transporte", "Administración",
            "Belleza", "Reparación", "Agricultura"
    };

    /** Ubicación estructurada: provincia + municipio + localidad. */
    private static void loc(Job j, String provinceCode, String municipality, String locality) {
        j.provinceCode = provinceCode;
        j.provinceName = CubaCatalog.provinceName(provinceCode);
        j.municipalityName = municipality;
        j.municipalityCode = CubaCatalog.municipalityCode(provinceCode, municipality);
        j.locality = locality;
    }

    /** Salario en CUP con periodo. */
    private static void pay(Job j, Integer min, Integer max, String period) {
        j.salaryMin = min;
        j.salaryMax = max;
        j.salaryPeriod = period;
    }

    /** Beneficios incluidos: comida, transporte, alojamiento, propinas. */
    private static void perks(Job j, boolean food, boolean transport, boolean accommodation, boolean tips) {
        j.includesFood = food;
        j.includesTransport = transport;
        j.includesAccommodation = accommodation;
        j.includesTips = tips;
    }

    private static void dates(Job j, int daysAgo) {
        j.publishedAt = TextUtils.daysAgoIso(daysAgo);
        j.createdAt = j.publishedAt;
        j.updatedAt = j.publishedAt;
    }

    public static List<Job> jobs() {
        List<Job> jobs = new ArrayList<>();

        // ---------- La Habana ----------
        Job j1 = new Job("demo-j1", "Cocinero/a de línea", "Restaurante El Jardín", "Vedado, La Habana");
        loc(j1, "HAB", "Plaza de la Revolución", "Vedado");
        j1.companyVerified = true; j1.category = "Gastronomía"; j1.workMode = "presencial";
        j1.contractType = "tiempo_completo"; pay(j1, 12000, 18000, "mensual");
        perks(j1, true, false, false, true); j1.contactHours = "9:00–17:00";
        j1.description = "Buscamos un cocinero o cocinera de línea para nuestro restaurante en el Vedado. Trabajarás en un equipo cercano y con mucho cariño por la cocina cubana e internacional. Turnos organizados y almuerzo incluido.";
        j1.responsibilities = Arrays.asList("Preparar platos de la carta con calidad y puntualidad", "Mantener limpia y ordenada tu estación de trabajo", "Colaborar con el equipo de cocina en el servicio diario");
        j1.requirements = Arrays.asList("Experiencia mínima de 2 años en cocina", "Conocimiento de cocina cubana", "Carnet de salud vigente");
        j1.benefits = Arrays.asList("Almuerzo incluido", "Propinas compartidas", "Dos días libres a la semana");
        j1.experienceMin = 2; j1.matchScore = 86; j1.whatsappEnabled = true; j1.contactPhone = "+5358964904";
        dates(j1, 0);
        jobs.add(j1);

        Job j2 = new Job("demo-j2", "Desarrollador Android (Java)", "TecnoHabana", "Miramar, La Habana");
        loc(j2, "HAB", "Playa", "Miramar");
        j2.companyVerified = true; j2.category = "Tecnología"; j2.workMode = "hibrido";
        j2.contractType = "tiempo_completo"; pay(j2, 25000, 40000, "mensual");
        j2.contactHours = "9:00–18:00";
        j2.description = "Únete a nuestro equipo de desarrollo móvil. Construimos aplicaciones Android en Java para clientes locales, con buenas prácticas, diseño cuidado y un ambiente de trabajo tranquilo y profesional.";
        j2.responsibilities = Arrays.asList("Desarrollar y mantener apps Android en Java", "Colaborar con diseño para lograr interfaces elegantes", "Participar en revisiones de código y planificación");
        j2.requirements = Arrays.asList("Java y Android SDK (3 años o más)", "Experiencia con REST y SQLite", "Git y trabajo en equipo");
        j2.benefits = Arrays.asList("Horario flexible", "Formación continua", "Equipo de trabajo proporcionado");
        j2.experienceMin = 3; j2.matchScore = 92; j2.whatsappEnabled = true; j2.contactPhone = "+5355551234";
        dates(j2, 1);
        jobs.add(j2);

        Job j3 = new Job("demo-j3", "Enfermero/a general", "Clínica Santa Fe", "Santa Fe, La Habana");
        loc(j3, "HAB", "Playa", "Santa Fe");
        j3.companyVerified = true; j3.category = "Salud y cuidado"; j3.workMode = "presencial";
        j3.contractType = "tiempo_completo"; pay(j3, 15000, 22000, "mensual");
        j3.contactHours = "8:00–16:00";
        j3.description = "Clínica privada en Santa Fe busca enfermero o enfermera general para atención ambulatoria. Valoramos el trato humano, la calma y el cuidado de cada paciente.";
        j3.responsibilities = Arrays.asList("Atención y seguimiento de pacientes ambulatorios", "Administración de tratamientos según indicación médica", "Registro cuidadoso de la historia clínica");
        j3.requirements = Arrays.asList("Título de enfermería", "Experiencia mínima de 1 año", "Trato amable y vocación de servicio");
        j3.benefits = Arrays.asList("Seguro de salud", "Capacitación pagada", "Guardias rotativas justas");
        j3.experienceMin = 1; j3.matchScore = 74; j3.whatsappEnabled = false;
        dates(j3, 1);
        jobs.add(j3);

        Job j4 = new Job("demo-j4", "Albañil oficial de primera", "Constructora Caribe", "Playa, La Habana");
        loc(j4, "HAB", "Playa", "Siboney");
        j4.companyVerified = false; j4.category = "Construcción"; j4.workMode = "presencial";
        j4.contractType = "por_proyecto"; pay(j4, 18000, 28000, "por_proyecto");
        perks(j4, false, true, false, false); j4.contactHours = "7:00–15:00";
        j4.description = "Obra residencial en Playa necesita albañiles oficiales de primera. Trabajo por proyecto de 6 meses, con posibilidad de continuar en la siguiente fase.";
        j4.responsibilities = Arrays.asList("Levantamiento de muros y acabados", "Lectura de planos sencillos", "Cumplimiento de normas de seguridad");
        j4.requirements = Arrays.asList("Experiencia mínima de 3 años", "Herramientas propias básicas", "Disponibilidad inmediata");
        j4.benefits = Arrays.asList("Transporte a la obra", "Pago quincenal puntual", "Equipos de protección incluidos");
        j4.experienceMin = 3; j4.matchScore = 68;
        dates(j4, 2);
        jobs.add(j4);

        Job j5 = new Job("demo-j5", "Profesor/a de inglés", "Academia Bright", "Centro Habana, La Habana");
        loc(j5, "HAB", "Centro Habana", "Barrio Chino");
        j5.companyVerified = true; j5.category = "Educación"; j5.workMode = "hibrido";
        j5.contractType = "medio_tiempo"; pay(j5, 10000, 16000, "mensual");
        j5.contactHours = "14:00–20:00";
        j5.description = "Academia de idiomas busca profesor o profesora de inglés para grupos de adultos y adolescentes. Clases amenas, grupos reducidos y material preparado.";
        j5.responsibilities = Arrays.asList("Impartir clases de inglés (niveles A2-B2)", "Preparar actividades y evaluaciones", "Seguimiento del progreso de cada alumno");
        j5.requirements = Arrays.asList("Nivel C1 de inglés o superior", "Experiencia docente de 1 año", "Paciencia y buena comunicación");
        j5.benefits = Arrays.asList("Horario de tarde", "Material didáctico incluido", "Ambiente familiar");
        j5.experienceMin = 1; j5.matchScore = 81; j5.whatsappEnabled = true; j5.contactPhone = "+5355559876";
        dates(j5, 2);
        jobs.add(j5);

        Job j6 = new Job("demo-j6", "Recepcionista bilingüe", "Hotel Vista Mar", "Miramar, La Habana");
        loc(j6, "HAB", "Playa", "Miramar");
        j6.companyVerified = true; j6.category = "Turismo"; j6.workMode = "presencial";
        j6.contractType = "tiempo_completo"; pay(j6, 14000, 20000, "mensual");
        perks(j6, true, false, false, true); j6.contactHours = "9:00–17:00";
        j6.description = "Hotel boutique en Miramar busca recepcionista bilingüe (español-inglés) para dar la bienvenida a nuestros huéspedes con calidez y profesionalidad.";
        j6.responsibilities = Arrays.asList("Atención y registro de huéspedes", "Gestión de reservas y llamadas", "Coordinación con housekeeping y restaurante");
        j6.requirements = Arrays.asList("Inglés fluido", "Experiencia en atención al cliente", "Buena presencia y trato cordial");
        j6.benefits = Arrays.asList("Uniforme incluido", "Comidas en turno", "Propinas");
        j6.experienceMin = 1; j6.matchScore = 77; j6.languages = Arrays.asList("Español", "Inglés");
        dates(j6, 3);
        jobs.add(j6);

        Job j7 = new Job("demo-j7", "Electricista residencial", "ServiHogar", "Marianao, La Habana");
        loc(j7, "HAB", "Marianao", "Los Quemados");
        j7.companyVerified = false; j7.category = "Servicios"; j7.workMode = "por_zonas";
        j7.contractType = "tiempo_completo"; pay(j7, null, null, "a_convenir");
        perks(j7, false, true, false, false); j7.contactHours = "8:00–18:00";
        j7.description = "Empresa de servicios del hogar busca electricista para instalaciones y reparaciones en viviendas de La Habana. Rutas organizadas por zonas y vehículo de empresa.";
        j7.responsibilities = Arrays.asList("Instalaciones eléctricas en viviendas", "Diagnóstico y reparación de averías", "Atención amable al cliente en su hogar");
        j7.requirements = Arrays.asList("Experiencia mínima de 2 años", "Licencia de conducir (deseable)", "Conocimientos de seguridad eléctrica");
        j7.benefits = Arrays.asList("Vehículo de empresa", "Herramientas proporcionadas", "Bonos por servicio");
        j7.experienceMin = 2; j7.matchScore = 63;
        dates(j7, 3);
        jobs.add(j7);

        Job j9 = new Job("demo-j9", "Chofer repartidor", "Distribuidora Rápido", "Boyeros, La Habana");
        loc(j9, "HAB", "Boyeros", "Calabazar");
        j9.companyVerified = true; j9.category = "Transporte"; j9.workMode = "por_zonas";
        j9.contractType = "tiempo_completo"; pay(j9, 16000, 24000, "mensual");
        perks(j9, false, true, false, false); j9.contactHours = "7:00–16:00";
        j9.description = "Reparto de mercancías en La Habana con rutas fijas y camión de empresa. Buscamos choferes responsables que conozcan la ciudad.";
        j9.responsibilities = Arrays.asList("Reparto diario por rutas asignadas", "Entrega y cobro con orden", "Cuidado del vehículo asignado");
        j9.requirements = Arrays.asList("Licencia de conducción vigente", "Conocimiento de La Habana", "Experiencia de 2 años");
        j9.benefits = Arrays.asList("Combustible incluido", "Pago por ruta extra", "Seguro del vehículo");
        j9.experienceMin = 2; j9.matchScore = 71;
        dates(j9, 5);
        jobs.add(j9);

        Job j10 = new Job("demo-j10", "Asistente administrativo/a", "Consultora Prisma", "Plaza, La Habana");
        loc(j10, "HAB", "Plaza de la Revolución", "Nuevo Vedado");
        j10.companyVerified = true; j10.category = "Administración"; j10.workMode = "presencial";
        j10.contractType = "tiempo_completo"; pay(j10, 13000, 19000, "mensual");
        j10.contactHours = "9:00–17:00";
        j10.description = "Consultora en Plaza busca asistente administrativo para gestión de agenda, documentos y atención a clientes. Ambiente de oficina tranquilo y ordenado.";
        j10.responsibilities = Arrays.asList("Gestión de agenda y correspondencia", "Archivo y control de documentos", "Atención telefónica y presencial");
        j10.requirements = Arrays.asList("Manejo de Office y correo", "Organización y discreción", "Experiencia de 1 año");
        j10.benefits = Arrays.asList("Horario de oficina", "Capacitación en gestión", "Café y merienda incluidos");
        j10.experienceMin = 1; j10.matchScore = 79; j10.whatsappEnabled = true; j10.contactPhone = "+5355552222";
        dates(j10, 5);
        jobs.add(j10);

        Job j11 = new Job("demo-j11", "Diseñador/a gráfico/a", "Estudio Kroma", "Remoto");
        j11.provinceCode = ""; j11.provinceName = ""; j11.municipalityName = "";
        j11.companyVerified = true; j11.category = "Tecnología"; j11.workMode = "remoto";
        j11.contractType = "por_proyecto"; pay(j11, 20000, 35000, "por_proyecto");
        j11.contactHours = "10:00–18:00";
        j11.description = "Estudio de diseño busca diseñador gráfico para identidad visual y redes sociales. Trabaja desde casa con un equipo creativo y proyectos bonitos.";
        j11.responsibilities = Arrays.asList("Diseño de piezas para redes y marca", "Propuestas de identidad visual", "Entrega puntual de archivos finales");
        j11.requirements = Arrays.asList("Portafolio de diseño", "Illustrator y Photoshop", "Buen gusto por la tipografía");
        j11.benefits = Arrays.asList("100% remoto", "Proyectos variados", "Pago por proyecto sin demoras");
        j11.experienceMin = 2; j11.matchScore = 88;
        dates(j11, 6);
        jobs.add(j11);

        // ---------- Occidente ----------
        Job p1 = new Job("demo-p1", "Guía de turismo rural", "Viñales Aventura", "Viñales, Pinar del Río");
        loc(p1, "PRI", "Viñales", "Valle de Viñales");
        p1.companyVerified = true; p1.category = "Turismo"; p1.workMode = "presencial";
        p1.contractType = "tiempo_completo"; pay(p1, 14000, 20000, "mensual");
        perks(p1, true, false, false, true); p1.contactHours = "8:00–17:00";
        p1.description = "Acompaña a visitantes por el Valle de Viñales: senderos, mogotes y la vida del campo. Buscamos a alguien que conozca la zona y disfrute contando su historia.";
        p1.responsibilities = Arrays.asList("Guiar grupos por senderos y miradores", "Explicar la naturaleza y la cultura local", "Cuidar la seguridad del grupo");
        p1.requirements = Arrays.asList("Conocimiento del Valle de Viñales", "Inglés básico (deseable)", "Buena condición física");
        p1.benefits = Arrays.asList("Almuerzo incluido", "Propinas", "Equipo de excursión");
        p1.experienceMin = 1; p1.matchScore = 72; p1.whatsappEnabled = true; p1.contactPhone = "+5355553311";
        dates(p1, 1);
        jobs.add(p1);

        Job p2 = new Job("demo-p2", "Operario/a de almacén", "Logística Mariel", "Mariel, Artemisa");
        loc(p2, "ART", "Mariel", "Zona Especial de Desarrollo");
        p2.companyVerified = true; p2.category = "Servicios"; p2.workMode = "presencial";
        p2.contractType = "tiempo_completo"; pay(p2, 6000, 8500, "quincenal");
        perks(p2, false, true, false, false); p2.contactHours = "7:00–15:00";
        p2.description = "Almacén logístico en Mariel busca operarios para recepción, orden y despacho de mercancías. Trabajo por turnos con transporte desde Artemisa.";
        p2.responsibilities = Arrays.asList("Recepción y revisión de mercancías", "Orden y control de inventario", "Preparación de pedidos para despacho");
        p2.requirements = Arrays.asList("Experiencia en almacén (deseable)", "Puntualidad y orden", "Disponibilidad para turnos");
        p2.benefits = Arrays.asList("Transporte incluido", "Pago quincenal puntual", "Uniforme y equipos");
        p2.experienceMin = 0; p2.matchScore = 65;
        dates(p2, 2);
        jobs.add(p2);

        Job p3 = new Job("demo-p3", "Técnico/a agrícola", "Finca La Esperanza", "Güines, Mayabeque");
        loc(p3, "MAY", "Güines", "La Catalina");
        p3.companyVerified = false; p3.category = "Agricultura"; p3.workMode = "presencial";
        p3.contractType = "tiempo_completo"; pay(p3, 10000, 15000, "mensual");
        perks(p3, true, false, true, false); p3.contactHours = "6:00–14:00";
        p3.description = "Finca familiar en Güines busca técnico agrícola para cultivos de viandas y hortalizas. Alojamiento en la finca y comida incluida.";
        p3.responsibilities = Arrays.asList("Siembra y cuidado de cultivos", "Riego y control de plagas", "Cosecha y selección de productos");
        p3.requirements = Arrays.asList("Experiencia en trabajo agrícola", "Conocimiento de cultivos de la zona", "Vivir cerca o aceptar alojamiento");
        p3.benefits = Arrays.asList("Alojamiento en la finca", "Comida incluida", "Productos de la finca");
        p3.experienceMin = 2; p3.matchScore = 58;
        dates(p3, 3);
        jobs.add(p3);

        Job p4 = new Job("demo-p4", "Animador/a turístico/a", "Hotel Sol Varadero", "Varadero, Matanzas");
        loc(p4, "MAT", "Cárdenas", "Varadero");
        p4.companyVerified = true; p4.category = "Turismo"; p4.workMode = "presencial";
        p4.contractType = "temporal"; pay(p4, 16000, 24000, "mensual");
        perks(p4, true, false, true, true); p4.contactHours = "9:00–18:00";
        p4.description = "Hotel en Varadero busca animador turístico para actividades de playa y espectáculos nocturnos. Temporada alta, con alojamiento y comidas.";
        p4.responsibilities = Arrays.asList("Organizar actividades recreativas", "Animar espectáculos y juegos", "Atender a los huéspedes con alegría");
        p4.requirements = Arrays.asList("Energía y carisma", "Inglés o francés básico", "Experiencia en animación (deseable)");
        p4.benefits = Arrays.asList("Alojamiento y comidas", "Propinas", "Uniforme incluido");
        p4.experienceMin = 1; p4.matchScore = 78; p4.whatsappEnabled = true; p4.contactPhone = "+5355554412";
        dates(p4, 0);
        jobs.add(p4);

        // ---------- Centro ----------
        Job c1 = new Job("demo-c1", "Soldador/a", "Taller Cienfuegos", "Cienfuegos");
        loc(c1, "CFG", "Cienfuegos", "Punta Gorda");
        c1.companyVerified = false; c1.category = "Reparación"; c1.workMode = "presencial";
        c1.contractType = "por_proyecto"; pay(c1, 20000, 30000, "por_proyecto");
        c1.contactHours = "8:00–16:00";
        c1.description = "Taller de estructuras metálicas busca soldador para proyectos de rejas, techos y portones. Pago por proyecto terminado, sin demoras.";
        c1.responsibilities = Arrays.asList("Soldadura de estructuras metálicas", "Lectura de medidas y planos sencillos", "Acabado y pintura de piezas");
        c1.requirements = Arrays.asList("Experiencia mínima de 2 años", "Soldadura eléctrica y autógena", "Equipos propios (deseable)");
        c1.benefits = Arrays.asList("Pago por proyecto", "Materiales incluidos", "Horario flexible");
        c1.experienceMin = 2; c1.matchScore = 66;
        dates(c1, 4);
        jobs.add(c1);

        Job c2 = new Job("demo-c2", "Cuidador/a de adulto mayor", "Hogar Santa Clara", "Santa Clara, Villa Clara");
        loc(c2, "VCL", "Santa Clara", "Reparto Escambray");
        c2.companyVerified = true; c2.category = "Salud y cuidado"; c2.workMode = "presencial";
        c2.contractType = "temporal"; pay(c2, 800, 1200, "diario");
        perks(c2, true, false, false, false); c2.contactHours = "8:00–20:00";
        c2.description = "Familia en Santa Clara busca cuidador o cuidadora para adulto mayor, con paciencia y cariño. Jornadas diurnas, pago por jornada.";
        c2.responsibilities = Arrays.asList("Acompañamiento y conversación", "Ayuda con alimentos y medicamentos", "Paseos cortos y cuidado diario");
        c2.requirements = Arrays.asList("Experiencia cuidando adultos mayores", "Paciencia y trato humano", "Referencias (deseable)");
        c2.benefits = Arrays.asList("Comida en jornada", "Pago diario puntual", "Descanso semanal");
        c2.experienceMin = 1; c2.matchScore = 70; c2.whatsappEnabled = true; c2.contactPhone = "+5355555523";
        dates(c2, 2);
        jobs.add(c2);

        Job c3 = new Job("demo-c3", "Artesano/a de cerámica", "Taller Trinidad", "Trinidad, Sancti Spíritus");
        loc(c3, "SSP", "Trinidad", "Centro histórico");
        c3.companyVerified = false; c3.category = "Comercio"; c3.workMode = "presencial";
        c3.contractType = "por_proyecto"; pay(c3, 15000, 25000, "por_proyecto");
        c3.contactHours = "9:00–17:00";
        c3.description = "Taller de cerámica en Trinidad busca artesano para piezas decorativas y souvenirs. Trabajo por encargo, con venta directa al turismo.";
        c3.responsibilities = Arrays.asList("Modelado y pintado de cerámica", "Horneado y acabado de piezas", "Atención a visitantes del taller");
        c3.requirements = Arrays.asList("Experiencia en cerámica", "Buen ojo para el detalle", "Creatividad");
        c3.benefits = Arrays.asList("Comisión por venta", "Materiales del taller", "Horario propio");
        c3.experienceMin = 2; c3.matchScore = 61;
        dates(c3, 6);
        jobs.add(c3);

        Job c4 = new Job("demo-c4", "Recepcionista de hotel", "Hotel Cayo Coco", "Morón, Ciego de Ávila");
        loc(c4, "CAV", "Morón", "Cayo Coco");
        c4.companyVerified = true; c4.category = "Turismo"; c4.workMode = "presencial";
        c4.contractType = "tiempo_completo"; pay(c4, 15000, 22000, "mensual");
        perks(c4, true, true, true, true); c4.contactHours = "8:00–16:00";
        c4.description = "Hotel en Cayo Coco busca recepcionista con inglés para turnos rotativos. Transporte desde Morón, alojamiento y comidas incluidos.";
        c4.responsibilities = Arrays.asList("Registro y atención de huéspedes", "Gestión de reservas", "Coordinación con servicios del hotel");
        c4.requirements = Arrays.asList("Inglés fluido", "Experiencia en hotelería (deseable)", "Trato cordial");
        c4.benefits = Arrays.asList("Transporte, alojamiento y comidas", "Propinas", "Uniforme incluido");
        c4.experienceMin = 1; c4.matchScore = 76; c4.languages = Arrays.asList("Español", "Inglés");
        dates(c4, 1);
        jobs.add(c4);

        Job c5 = new Job("demo-c5", "Mecánico/a de taller", "Taller Camagüey", "Camagüey");
        loc(c5, "CAM", "Camagüey", "Reparto La Vigía");
        c5.companyVerified = false; c5.category = "Reparación"; c5.workMode = "presencial";
        c5.contractType = "tiempo_completo"; pay(c5, 18000, 26000, "mensual");
        c5.contactHours = "8:00–17:00";
        c5.description = "Taller mecánico en Camagüey busca mecánico para mantenimiento y reparación de autos. Clientes fijos y trabajo estable.";
        c5.responsibilities = Arrays.asList("Diagnóstico de averías", "Mantenimiento y reparación", "Pruebas de funcionamiento");
        c5.requirements = Arrays.asList("Experiencia mínima de 3 años", "Conocimiento de motores diésel y gasolina", "Herramientas propias (deseable)");
        c5.benefits = Arrays.asList("Bono por trabajo terminado", "Herramientas del taller", "Horario fijo");
        c5.experienceMin = 3; c5.matchScore = 69;
        dates(c5, 5);
        jobs.add(c5);

        // ---------- Oriente ----------
        Job o1 = new Job("demo-o1", "Vendedor/a de tienda", "Comercial Las Tunas", "Las Tunas");
        loc(o1, "LTU", "Las Tunas", "Centro");
        o1.companyVerified = false; o1.category = "Comercio"; o1.workMode = "presencial";
        o1.contractType = "tiempo_completo"; pay(o1, 3000, 4500, "semanal");
        perks(o1, false, false, false, true); o1.contactHours = "9:00–18:00";
        o1.description = "Tienda de variedades en Las Tunas busca vendedor o vendedora con buen trato y ganas de atender a los vecinos. Pago semanal más comisión por ventas.";
        o1.responsibilities = Arrays.asList("Atención al cliente en tienda", "Cobro y control de stock", "Orden y limpieza del local");
        o1.requirements = Arrays.asList("Experiencia en ventas (deseable)", "Honestidad y puntualidad", "Vivir cerca del centro");
        o1.benefits = Arrays.asList("Comisión por ventas", "Descuento en tienda", "Domingos libres");
        o1.experienceMin = 0; o1.matchScore = 57;
        dates(o1, 3);
        jobs.add(o1);

        Job o2 = new Job("demo-o2", "Cocinero/a", "Restaurante Holguín", "Holguín");
        loc(o2, "HOL", "Holguín", "Centro");
        o2.companyVerified = true; o2.category = "Gastronomía"; o2.workMode = "presencial";
        o2.contractType = "tiempo_completo"; pay(o2, 12000, 18000, "mensual");
        perks(o2, true, false, false, true); o2.contactHours = "9:00–17:00";
        o2.description = "Restaurante familiar en Holguín busca cocinero o cocinera para cocina cubana y criolla. Ambiente cercano y turnos organizados.";
        o2.responsibilities = Arrays.asList("Preparación de platos de la carta", "Control de insumos y frescura", "Limpieza de la cocina");
        o2.requirements = Arrays.asList("Experiencia mínima de 2 años", "Cocina cubana", "Carnet de salud vigente");
        o2.benefits = Arrays.asList("Almuerzo incluido", "Propinas compartidas", "Un día libre semanal");
        o2.experienceMin = 2; o2.matchScore = 73; o2.whatsappEnabled = true; o2.contactPhone = "+5355556634";
        dates(o2, 2);
        jobs.add(o2);

        Job o3 = new Job("demo-o3", "Chofer de camión", "Transporte Granma", "Bayamo, Granma");
        loc(o3, "GRA", "Bayamo", "Reparto Siboney");
        o3.companyVerified = true; o3.category = "Transporte"; o3.workMode = "por_zonas";
        o3.contractType = "tiempo_completo"; pay(o3, 20000, 30000, "mensual");
        perks(o3, false, true, false, false); o3.contactHours = "6:00–15:00";
        o3.description = "Transporte de mercancías por Granma y Oriente con camión de empresa. Rutas por zonas, con viáticos y descanso entre viajes.";
        o3.responsibilities = Arrays.asList("Transporte por rutas asignadas", "Entrega con orden y puntualidad", "Revisión básica del vehículo");
        o3.requirements = Arrays.asList("Licencia de conducción (categoría C)", "Experiencia de 3 años", "Conocimiento de Oriente");
        o3.benefits = Arrays.asList("Viáticos por viaje", "Combustible incluido", "Seguro del vehículo");
        o3.experienceMin = 3; o3.matchScore = 71;
        dates(o3, 4);
        jobs.add(o3);

        Job o4 = new Job("demo-o4", "Estilista", "Salón Santiago", "Santiago de Cuba");
        loc(o4, "SCU", "Santiago de Cuba", "Vista Alegre");
        o4.companyVerified = false; o4.category = "Belleza"; o4.workMode = "presencial";
        o4.contractType = "tiempo_completo"; pay(o4, 1000, 1800, "diario");
        perks(o4, false, false, false, true); o4.contactHours = "9:00–19:00";
        o4.description = "Salón de belleza en Vista Alegre busca estilista para cortes, color y peinados. Clientela fija, pago diario más propinas.";
        o4.responsibilities = Arrays.asList("Cortes y peinados", "Color y tratamientos", "Atención cercana al cliente");
        o4.requirements = Arrays.asList("Experiencia en peluquería", "Buen gusto y trato amable", "Portafolio o referencias (deseable)");
        o4.benefits = Arrays.asList("Propinas diarias", "Productos del salón", "Horario flexible");
        o4.experienceMin = 1; o4.matchScore = 64; o4.whatsappEnabled = true; o4.contactPhone = "+5355557745";
        dates(o4, 1);
        jobs.add(o4);

        Job o5 = new Job("demo-o5", "Profesor/a de música", "Escuela Guantánamo", "Guantánamo");
        loc(o5, "GUA", "Guantánamo", "Centro");
        o5.companyVerified = true; o5.category = "Educación"; o5.workMode = "presencial";
        o5.contractType = "medio_tiempo"; pay(o5, 9000, 14000, "mensual");
        o5.contactHours = "14:00–18:00";
        o5.description = "Escuela de arte en Guantánamo busca profesor o profesora de música para niños y jóvenes. Clases de instrumento y canto, en un ambiente alegre.";
        o5.responsibilities = Arrays.asList("Clases de música para niños y jóvenes", "Preparación de presentaciones", "Seguimiento del progreso de cada alumno");
        o5.requirements = Arrays.asList("Formación musical", "Experiencia docente (deseable)", "Paciencia y cariño con los niños");
        o5.benefits = Arrays.asList("Horario de tarde", "Instrumentos de la escuela", "Ambiente familiar");
        o5.experienceMin = 1; o5.matchScore = 67;
        dates(o5, 7);
        jobs.add(o5);

        // ---------- Municipio especial ----------
        Job ij = new Job("demo-ij", "Técnico/a de mantenimiento", "Hotel Isla", "Isla de la Juventud");
        loc(ij, "IJ", "Isla de la Juventud", "Nueva Gerona");
        ij.companyVerified = true; ij.category = "Servicios"; ij.workMode = "presencial";
        ij.contractType = "tiempo_completo"; pay(ij, 16000, 22000, "mensual");
        perks(ij, true, true, true, false); ij.contactHours = "8:00–16:00";
        ij.description = "Hotel en Nueva Gerona busca técnico de mantenimiento para electricidad, plomería y equipos. Alojamiento, comidas y transporte incluidos.";
        ij.responsibilities = Arrays.asList("Mantenimiento preventivo de instalaciones", "Reparaciones de electricidad y plomería", "Atención de averías urgentes");
        ij.requirements = Arrays.asList("Experiencia mínima de 2 años", "Conocimientos de electricidad y plomería", "Disponibilidad para guardias");
        ij.benefits = Arrays.asList("Alojamiento y comidas", "Transporte incluido", "Uniforme y herramientas");
        ij.experienceMin = 2; ij.matchScore = 75; ij.whatsappEnabled = true; ij.contactPhone = "+5355558856";
        dates(ij, 0);
        jobs.add(ij);

        return jobs;
    }

    /** Ofertas propias de la empresa demo. */
    public static List<Job> myCompanyJobs() {
        List<Job> jobs = new ArrayList<>();
        Job a = new Job("demo-my1", "Cocinero/a de línea", "Mi Empresa Demo", "Vedado, La Habana");
        loc(a, "HAB", "Plaza de la Revolución", "Vedado");
        a.companyVerified = true; a.category = "Gastronomía"; a.workMode = "presencial";
        a.contractType = "tiempo_completo"; pay(a, 12000, 18000, "mensual");
        perks(a, true, false, false, true); a.contactHours = "9:00–17:00";
        a.description = "Borrador de ejemplo: cocina de línea para restaurante en el Vedado.";
        a.status = "published";
        a.publishedAt = TextUtils.daysAgoIso(2); a.createdAt = a.publishedAt; a.updatedAt = a.publishedAt;
        jobs.add(a);

        Job b = new Job("demo-my2", "Mesero/a turno mañana", "Mi Empresa Demo", "Vedado, La Habana");
        loc(b, "HAB", "Plaza de la Revolución", "Vedado");
        b.companyVerified = true; b.category = "Gastronomía"; b.workMode = "presencial";
        b.contractType = "medio_tiempo"; pay(b, 8000, 12000, "mensual");
        perks(b, false, false, false, true);
        b.description = "Borrador guardado: mesero para turnos de mañana.";
        b.status = "draft";
        b.createdAt = TextUtils.daysAgoIso(1); b.updatedAt = b.createdAt;
        jobs.add(b);

        Job c = new Job("demo-my3", "Ayudante de cocina", "Mi Empresa Demo", "Vedado, La Habana");
        loc(c, "HAB", "Plaza de la Revolución", "Vedado");
        c.companyVerified = true; c.category = "Gastronomía"; c.workMode = "presencial";
        c.contractType = "temporal"; pay(c, 9000, 11000, "mensual");
        c.description = "Oferta pausada de ejemplo.";
        c.status = "paused";
        c.publishedAt = TextUtils.daysAgoIso(12); c.createdAt = c.publishedAt; c.updatedAt = TextUtils.daysAgoIso(1);
        jobs.add(c);
        return jobs;
    }

    public static List<AppNotification> welcomeNotifications() {
        List<AppNotification> list = new ArrayList<>();
        AppNotification n1 = new AppNotification();
        n1.notificationId = "demo-n1";
        n1.type = "new_relevant_job";
        n1.title = "Nueva oferta que combina contigo";
        n1.body = "Desarrollador Android (Java) en TecnoHabana · 92% de coincidencia";
        n1.entityId = "demo-j2";
        n1.route = "job/demo-j2";
        n1.read = false;
        n1.createdAt = TextUtils.daysAgoIso(0);
        list.add(n1);

        AppNotification n2 = new AppNotification();
        n2.notificationId = "demo-n2";
        n2.type = "saved_search_digest";
        n2.title = "Bienvenido a CubaPincha";
        n2.body = "Completa tu perfil para recibir ofertas que realmente combinen contigo.";
        n2.entityId = "";
        n2.route = "profile";
        n2.read = false;
        n2.createdAt = TextUtils.daysAgoIso(0);
        list.add(n2);
        return list;
    }

    /** Candidatos de ejemplo para la empresa demo. */
    public static List<Application> demoCandidates() {
        List<Application> list = new ArrayList<>();

        Application a1 = new Application();
        a1.applicationId = "demo-a1"; a1.jobId = "demo-my1"; a1.jobTitle = "Cocinero/a de línea";
        a1.jobCity = "Vedado, La Habana"; a1.jobProvinceName = "La Habana"; a1.jobMunicipalityName = "Plaza de la Revolución";
        a1.jobWorkMode = "presencial";
        a1.workerName = "María González"; a1.workerHeadline = "Cocinera con 5 años de experiencia";
        a1.workerCity = "Vedado, La Habana"; a1.workerProvinceName = "La Habana"; a1.workerMunicipalityName = "Plaza de la Revolución";
        a1.workerSkills = Arrays.asList("Cocina cubana", "Repostería", "Gestión de cocina");
        a1.workerExperienceYears = 5; a1.matchScore = 91; a1.hasCv = true;
        a1.status = "reviewing"; a1.createdAt = TextUtils.daysAgoIso(1); a1.updatedAt = a1.createdAt;
        list.add(a1);

        Application a2 = new Application();
        a2.applicationId = "demo-a2"; a2.jobId = "demo-my1"; a2.jobTitle = "Cocinero/a de línea";
        a2.jobCity = "Vedado, La Habana"; a2.jobProvinceName = "La Habana"; a2.jobMunicipalityName = "Plaza de la Revolución";
        a2.jobWorkMode = "presencial";
        a2.workerName = "Carlos Pérez"; a2.workerHeadline = "Cocinero de línea";
        a2.workerCity = "Plaza, La Habana"; a2.workerProvinceName = "La Habana"; a2.workerMunicipalityName = "Plaza de la Revolución";
        a2.workerSkills = Arrays.asList("Cocina internacional", "Parrilla");
        a2.workerExperienceYears = 3; a2.matchScore = 78; a2.hasCv = false;
        a2.status = "sent"; a2.createdAt = TextUtils.daysAgoIso(0); a2.updatedAt = a2.createdAt;
        list.add(a2);

        Application a3 = new Application();
        a3.applicationId = "demo-a3"; a3.jobId = "demo-my1"; a3.jobTitle = "Cocinero/a de línea";
        a3.jobCity = "Vedado, La Habana"; a3.jobProvinceName = "La Habana"; a3.jobMunicipalityName = "Plaza de la Revolución";
        a3.jobWorkMode = "presencial";
        a3.workerName = "Ana Rodríguez"; a3.workerHeadline = "Ayudante de cocina, en formación";
        a3.workerCity = "Centro Habana, La Habana"; a3.workerProvinceName = "La Habana"; a3.workerMunicipalityName = "Centro Habana";
        a3.workerSkills = Arrays.asList("Cocina cubana", "Limpieza y orden");
        a3.workerExperienceYears = 1; a3.matchScore = 64; a3.hasCv = false;
        a3.status = "interview"; a3.createdAt = TextUtils.daysAgoIso(2); a3.updatedAt = TextUtils.daysAgoIso(0);
        list.add(a3);

        Application a4 = new Application();
        a4.applicationId = "demo-a4"; a4.jobId = "demo-my3"; a4.jobTitle = "Ayudante de cocina";
        a4.jobCity = "Vedado, La Habana"; a4.jobProvinceName = "La Habana"; a4.jobMunicipalityName = "Plaza de la Revolución";
        a4.jobWorkMode = "presencial";
        a4.workerName = "Luis Martínez"; a4.workerHeadline = "Ayudante de cocina";
        a4.workerCity = "Marianao, La Habana"; a4.workerProvinceName = "La Habana"; a4.workerMunicipalityName = "Marianao";
        a4.workerSkills = Arrays.asList("Cocina", "Almacén");
        a4.workerExperienceYears = 2; a4.matchScore = 72; a4.hasCv = true;
        a4.status = "sent"; a4.createdAt = TextUtils.daysAgoIso(3); a4.updatedAt = a4.createdAt;
        list.add(a4);

        return list;
    }
}
