{{- define "swiftdrop.databaseEnv" -}}
{{- $prefix := .prefix -}}
- name: SPRING_DATASOURCE_URL
  valueFrom:
    secretKeyRef: {name: {{ .root.Values.existingSecret }}, key: {{ printf "%s_DB_URL" $prefix }}}
- name: SPRING_DATASOURCE_USERNAME
  valueFrom:
    secretKeyRef: {name: {{ .root.Values.existingSecret }}, key: {{ printf "%s_DB_USERNAME" $prefix }}}
- name: SPRING_DATASOURCE_PASSWORD
  valueFrom:
    secretKeyRef: {name: {{ .root.Values.existingSecret }}, key: {{ printf "%s_DB_PASSWORD" $prefix }}}
{{- end }}

{{- define "swiftdrop.rabbitEnv" -}}
- name: SPRING_RABBITMQ_USERNAME
  valueFrom:
    secretKeyRef: {name: {{ .Values.existingSecret }}, key: RABBITMQ_USERNAME}
- name: SPRING_RABBITMQ_PASSWORD
  valueFrom:
    secretKeyRef: {name: {{ .Values.existingSecret }}, key: RABBITMQ_PASSWORD}
{{- end }}

{{- define "swiftdrop.redisEnv" -}}
- name: SPRING_DATA_REDIS_PASSWORD
  valueFrom:
    secretKeyRef: {name: {{ .Values.existingSecret }}, key: REDIS_PASSWORD}
{{- end }}

{{- define "swiftdrop.applicationWorkload" -}}
{{- $root := .root -}}
{{- $name := .name -}}
{{- $service := index $root.Values.services $name -}}
{{- if $service.enabled }}
apiVersion: v1
kind: Service
metadata:
  name: {{ $name }}
  labels:
    {{- include "swiftdrop.labels" $root | nindent 4 }}
    app.kubernetes.io/component: {{ $name }}
spec:
  type: ClusterIP
  selector:
    app.kubernetes.io/instance: {{ $root.Release.Name }}
    app.kubernetes.io/component: {{ $name }}
  ports:
    - name: http
      port: {{ $service.port }}
      targetPort: http
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ $name }}
  labels:
    {{- include "swiftdrop.labels" $root | nindent 4 }}
    app.kubernetes.io/component: {{ $name }}
spec:
  replicas: {{ $service.replicas }}
  revisionHistoryLimit: 3
  strategy:
    type: RollingUpdate
    rollingUpdate: {maxUnavailable: 0, maxSurge: 1}
  selector:
    matchLabels:
      app.kubernetes.io/instance: {{ $root.Release.Name }}
      app.kubernetes.io/component: {{ $name }}
  template:
    metadata:
      labels:
        app.kubernetes.io/instance: {{ $root.Release.Name }}
        app.kubernetes.io/component: {{ $name }}
      annotations:
        checksum/config: {{ include "swiftdrop.configChecksum" $root }}
        {{- with $root.Values.global.podAnnotations }}
        {{- toYaml . | nindent 8 }}
        {{- end }}
    spec:
      serviceAccountName: {{ include "swiftdrop.serviceAccountName" (dict "root" $root "service" $name) }}
      automountServiceAccountToken: false
      securityContext:
        {{- toYaml $root.Values.podSecurityContext | nindent 8 }}
      topologySpreadConstraints:
        - maxSkew: 1
          topologyKey: kubernetes.io/hostname
          whenUnsatisfiable: ScheduleAnyway
          labelSelector:
            matchLabels:
              app.kubernetes.io/instance: {{ $root.Release.Name }}
              app.kubernetes.io/component: {{ $name }}
      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
            - weight: 100
              podAffinityTerm:
                topologyKey: kubernetes.io/hostname
                labelSelector:
                  matchLabels:
                    app.kubernetes.io/instance: {{ $root.Release.Name }}
                    app.kubernetes.io/component: {{ $name }}
      {{- with $root.Values.global.imagePullSecrets }}
      imagePullSecrets:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      containers:
        - name: {{ $name }}
          image: {{ include "swiftdrop.image" (dict "root" $root "service" $service) | quote }}
          imagePullPolicy: {{ $root.Values.global.imagePullPolicy }}
          securityContext:
            {{- toYaml $root.Values.containerSecurityContext | nindent 12 }}
            readOnlyRootFilesystem: {{ if hasKey $service "readOnlyRootFilesystem" }}{{ $service.readOnlyRootFilesystem }}{{ else }}true{{ end }}
          ports:
            - {name: http, containerPort: {{ $service.port }}}
          envFrom:
            - configMapRef: {name: {{ include "swiftdrop.fullname" $root }}-config}
          {{- with .env }}
          env:
            {{- tpl . $root | nindent 12 }}
          {{- end }}
          startupProbe:
            httpGet: {path: {{ $service.probePath }}, port: http}
            failureThreshold: 30
            periodSeconds: 5
          readinessProbe:
            httpGet: {path: {{ $service.probePath }}, port: http}
            periodSeconds: 10
            timeoutSeconds: 3
          livenessProbe:
            httpGet: {path: {{ $service.probePath }}, port: http}
            initialDelaySeconds: 30
            periodSeconds: 20
            timeoutSeconds: 3
          resources:
            {{- toYaml $service.resources | nindent 12 }}
          volumeMounts:
            - {name: tmp, mountPath: /tmp}
      volumes:
        - name: tmp
          emptyDir: {sizeLimit: 128Mi}
{{- end }}
{{- end }}
