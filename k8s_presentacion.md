````carousel
# ☸️ ¿Por qué y cuándo usar Kubernetes?
**Automatización y Orquestación de Contenedores**

*Una guía completa para entender el estándar de la industria en despliegue de software.*
<!-- slide -->
# 📌 ¿Qué es Kubernetes (K8s)?

- **Plataforma Open-Source**: Diseñada para automatizar el despliegue, escalado y operación de aplicaciones en contenedores.
- **Orquestador**: Mientras Docker empaqueta la aplicación, Kubernetes es el "director de orquesta" que decide dónde y cómo se ejecutan esos paquetes.
- **Origen**: Creado originalmente por Google (basado en su sistema interno Borg) y ahora gestionado por la CNCF (Cloud Native Computing Foundation).
<!-- slide -->
# 🛠️ Problemas que resuelve

Antes de Kubernetes, gestionar contenedores en producción era un caos:
- **Escalabilidad manual**: Era difícil escalar contenedores arriba/abajo rápidamente según el tráfico.
- **Tolerancia a fallos**: Si un contenedor moría en la madrugada, alguien tenía que reiniciarlo manualmente.
- **Inconsistencia**: "En mi máquina funciona", pero los ambientes de Dev, Staging y Prod eran distintos.

**Kubernetes automatiza la recuperación (self-healing), el autoescalado y permite actualizaciones sin tiempo de inactividad (Zero Downtime).**
<!-- slide -->
# 🏗️ Arquitectura y Componentes Clave

Kubernetes se divide en dos partes principales:

### 1. Control Plane (El Cerebro)
- **API Server**: El punto de entrada para todas las comunicaciones.
- **etcd**: Base de datos clave-valor que guarda el estado del clúster.
- **Scheduler**: Decide en qué nodo se ejecuta cada Pod.

### 2. Worker Nodes (Los Trabajadores)
- **Kubelet**: El agente que asegura que los contenedores estén corriendo.
- **Pod**: La unidad mínima de ejecución en K8s (puede tener uno o más contenedores).
- **Kube-Proxy**: Maneja las reglas de red.
<!-- slide -->
# 🧩 Otros Conceptos Clave

- **Deployment**: Define el estado deseado (ej. "quiero 3 réplicas de este Pod en todo momento").
- **Service**: Provee una dirección IP y nombre de dominio estable para acceder a los Pods, actuando como balanceador de carga interno.
- **Ingress**: Expone rutas HTTP/HTTPS desde el exterior hacia los servicios del clúster.
- **ConfigMap & Secret**: Permiten inyectar configuración y contraseñas a los Pods sin reconstruir la imagen.
<!-- slide -->
# 🚀 Beneficios para Empresas

1. **Alta Disponibilidad (Self-Healing)**: Reinicia automáticamente los contenedores que fallan o reemplaza nodos caídos.
2. **Escalabilidad Automática**: Puede agregar más Pods si hay un pico de usuarios (HPA).
3. **Portabilidad Híbrida**: Funciona en AWS, Azure, GCP o en tus propios servidores locales sin cambiar el código.
4. **Despliegues Seguros**: Permite hacer "Rolling Updates" para actualizar versiones sin que el usuario note una caída.
<!-- slide -->
# 💡 Casos de Uso Comunes

- **Arquitecturas de Microservicios**: Escalar cada servicio de manera independiente (ej. el servicio de Historial Médico vs. el Gateway).
- **APIs REST de alto tráfico**: Balancear millones de peticiones diarias.
- **Pipelines CI/CD**: Integración y despliegues totalmente automatizados con rollback inmediato si algo falla.
- **Sistemas de Big Data y Machine Learning**: Coordinación eficiente de tareas distribuidas intensivas.
<!-- slide -->
# ✅ Cuándo SÍ usar Kubernetes

- Cuando tienes **múltiples microservicios** interactuando entre sí.
- Cuando necesitas **escalabilidad horizontal** automática y sin intervención humana.
- Cuando quieres evitar el "Vendor Lock-in" (ser independiente del proveedor de nube).
- Cuando cuentas con un **equipo DevOps** preparado para asumir la curva de aprendizaje.
<!-- slide -->
# ❌ Cuándo NO usar Kubernetes

- Si tienes una **aplicación monolítica pequeña** o un simple blog en WordPress.
- Si no hay experiencia previa con contenedores (Docker).
- Si el **costo de infraestructura y la complejidad** operativa no se justifican frente al tráfico de la app.
- Si no requieres alta disponibilidad real (ej. sistemas internos de uso esporádico).
<!-- slide -->
# ☁️ Kubernetes en la Nube (Servicios Gestionados)

La forma más común de usar K8s en producción es a través de servicios gestionados, donde la nube se encarga del *Control Plane*:

- **AWS**: EKS (Elastic Kubernetes Service)
- **Azure**: AKS (Azure Kubernetes Service)
- **GCP**: GKE (Google Kubernetes Engine)

**Ventajas**: Administración simplificada, seguridad nativa e integración perfecta con balanceadores de carga y discos duros de la nube.
<!-- slide -->
# 🥊 Docker Compose vs Kubernetes

| Característica | Docker Compose | Kubernetes |
| :--- | :--- | :--- |
| **Enfoque** | Entornos locales y desarrollo | Producción, clústeres a gran escala |
| **Escalabilidad** | Manual (1 máquina) | Automática a través de múltiples máquinas |
| **Alta Disponibilidad**| No nativa | Nativa (Autocorrección y recuperación) |
| **Balanceo de Carga** | Básico | Avanzado (Services, Ingress) |
| **Complejidad** | Muy baja | Alta (Curva de aprendizaje pronunciada) |
| **Gestión de Secretos**| Limitada (Variables de entorno) | Nativa y encriptada (Secrets) |
<!-- slide -->
# 🩺 Nuestro Sistema de Salud en K8s

La carpeta `k8s/` describe el sistema completo: 3 bases de datos, Keycloak y 5 microservicios. Los archivos están numerados en el orden en que deben aplicarse.

| Archivo | Qué crea | Por qué va en ese orden |
| :--- | :--- | :--- |
| `01-config.yaml` | ConfigMap + Secret | Los demás Pods leen sus variables de aquí |
| `02-databases.yaml` | PostgreSQL · MongoDB · MySQL (+ PVC) | Los servicios necesitan sus bases listas |
| `02b-rabbitmq.yaml` | RabbitMQ (+ PVC) | Los servicios se hablan por sus colas |
| `03-keycloak.yaml` | Identity Provider | Emite los tokens JWT |
| `04-eureka.yaml` | Service Discovery | Todos se registran aquí al arrancar |
| `05-microservices.yaml` | Pacientes · Historial · FastAPI | Se registran en Eureka |
| `06-gateway.yaml` | API Gateway (NodePort) | Único servicio expuesto hacia afuera |
<!-- slide -->
# 🚀 Demo: del código al clúster

**1. Construir las imágenes DENTRO de Minikube**
Los manifiestos usan `imagePullPolicy: Never`: las imágenes deben existir en el nodo, no en Docker Hub.
```bash
minikube start --cpus=4 --memory=8192
eval $(minikube docker-env)     # ⚠️ el paso que más se olvida
docker build -t eureka-server:latest ./eureka-server
```

**2. Cargar el realm de Keycloak**
```bash
kubectl create configmap keycloak-realm-config \
  --from-file=realm-export.json=keycloak/realm-export.json
```

**3. Desplegar y observar**
```bash
kubectl apply -f k8s/
kubectl get pods -w
```

**4. Abrir los puertos hacia tu máquina**
```bash
kubectl port-forward svc/keycloak-salud 8080:8080
kubectl port-forward svc/gateway-service 8090:8090
```
<!-- slide -->
# 💪 Self-Healing y Escalado en vivo

**Mata un Pod... y renace solo:**
```bash
kubectl delete pod -l app=pacientes-service
kubectl get pods -l app=pacientes-service -w
```
El Deployment detecta que hay 0 réplicas cuando debería haber 1 y crea un Pod nuevo. **Nadie tuvo que intervenir.**

**Escala horizontalmente:**
```bash
kubectl scale deployment pacientes-service --replicas=3
```
Las 3 réplicas se registran solas en Eureka y el Gateway empieza a balancear entre ellas: por eso las rutas usan `lb://PACIENTES-SERVICE`.

> Esta es la diferencia real entre `kubectl run` (un Pod suelto que muere y no vuelve) y un **Deployment**.
<!-- slide -->
# 🏁 Conclusión y Preguntas

**Kubernetes no es una moda, es el estándar operativo que transformó el despliegue de software.**
Brinda resiliencia, eficiencia y automatización, pero conlleva una gran responsabilidad técnica.

*Úsalo cuando la necesidad de escalar y la complejidad de tu sistema realmente lo exijan.*

### ¿Preguntas?
¿Está preparado tu equipo (y tu proyecto) para dar el salto a Kubernetes?
````
