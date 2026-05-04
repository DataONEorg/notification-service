{{/*
Expand the name of the chart.
*/}}
{{- define "notifications.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "notifications.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create a default fully qualified app name for the embedded RabbitMQ Cluster Operator Deployment.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by DNS naming spec)
*/}}
{{- define "notifications.rmq.fullname" -}}
{{- $name := default "rmq" .Values.rmq.rmqOperator.nameOverride | trunc 63 | trimSuffix "-" }}
{{- printf "%s-%s" .Release.Name $name }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "notifications.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "notifications.labels" -}}
helm.sh/chart: {{ include "notifications.chart" . }}
{{ include "notifications.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "notifications.selectorLabels" -}}
app.kubernetes.io/name: {{ include "notifications.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "notifications.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "notifications.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Create a checksum that reflects changes in the config files.
Do it here, instead of in deployment.yaml, because helm's ordering of operations means that the
checksum is performed before the values overrides are inserted into the config files, so the
checksum doesn't change when those values do.
*/}}
{{- define "notifications.config.checksum" }}
{{- $out := "" }}
{{- range $path, $file := .Files.Glob "config/*" }}
  {{- $content := toString $file }}
  {{- $rendered := tpl $content $ }}
  {{- $out = printf "%s\n%s" $out $rendered }}
{{- end }}
{{- $out | trim | sha256sum -}}
{{- end -}}

{{- define "nsconsumer.name" -}}
{{- default "nsconsumer" .Values.nsconsumer.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "nsconsumer.instance" -}}
{{- printf "%s-%s" .Values.nsconsumer.nameOverride .Release.Name | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create name for nsconsumer
*/}}
{{- define "nsconsumer.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- "nsconsumer" .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default "nsconsumer" .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "nsconsumer.labels" -}}
helm.sh/chart: {{ include "notifications.chart" . }}
{{ include "notifications.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}