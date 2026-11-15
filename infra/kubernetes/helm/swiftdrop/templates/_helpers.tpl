{{- define "swiftdrop.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "swiftdrop.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name (include "swiftdrop.name" .) | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}

{{- define "swiftdrop.labels" -}}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | quote }}
app.kubernetes.io/name: {{ include "swiftdrop.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- with .Values.global.commonLabels }}
{{ toYaml . }}
{{- end }}
{{- end }}

{{- define "swiftdrop.serviceAccountName" -}}
{{- printf "%s-%s" (include "swiftdrop.fullname" .root) .service | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "swiftdrop.image" -}}
{{- if .root.Values.global.imageRegistry -}}
{{- printf "%s/%s:%s" .root.Values.global.imageRegistry .service.image.repository .service.image.tag -}}
{{- else -}}
{{- printf "%s:%s" .service.image.repository .service.image.tag -}}
{{- end -}}
{{- end }}
