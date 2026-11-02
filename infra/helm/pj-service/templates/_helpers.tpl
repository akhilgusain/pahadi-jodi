{{- define "pj.name" -}}{{ default .Release.Name .Values.nameOverride | trunc 52 | trimSuffix "-" }}{{- end }}
{{- define "pj.labels" -}}
app.kubernetes.io/name: {{ include "pj.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Values.image.tag | quote }}
app.kubernetes.io/part-of: pahadi-jodi
app.kubernetes.io/managed-by: {{ .Release.Service }}
pj/env: {{ .Values.env }}
{{- end }}
{{- define "pj.selector" -}}
app.kubernetes.io/name: {{ include "pj.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}
{{- define "pj.sa" -}}{{ if .Values.serviceAccount.name }}{{ .Values.serviceAccount.name }}{{ else }}{{ include "pj.name" . }}{{ end }}{{- end }}
{{- define "pj.podspec" -}}
serviceAccountName: {{ include "pj.sa" . }}
terminationGracePeriodSeconds: {{ .Values.terminationGracePeriodSeconds }}
securityContext: {{- toYaml .Values.podSecurityContext | nindent 2 }}
{{- if .Values.topologySpread.enabled }}
topologySpreadConstraints:
  - maxSkew: {{ .Values.topologySpread.maxSkew }}
    topologyKey: topology.kubernetes.io/zone
    whenUnsatisfiable: DoNotSchedule
    labelSelector: {matchLabels: {{- include "pj.selector" . | nindent 8 }}}
  - maxSkew: 2
    topologyKey: kubernetes.io/hostname
    whenUnsatisfiable: ScheduleAnyway
    labelSelector: {matchLabels: {{- include "pj.selector" . | nindent 8 }}}
{{- end }}
nodeSelector:
  pj/pool: {{ .Values.nodePool }}
  {{- with .Values.nodeSelector }}{{ toYaml . | nindent 2 }}{{- end }}
{{- with .Values.tolerations }}
tolerations: {{- toYaml . | nindent 2 }}
{{- end }}
{{- with .Values.affinity }}
affinity: {{- toYaml . | nindent 2 }}
{{- end }}
containers:
  - name: app
    image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
    imagePullPolicy: {{ .Values.image.pullPolicy }}
    ports:
      - {name: http, containerPort: {{ .Values.port }}}
      - {name: management, containerPort: {{ .Values.managementPort }}}
      {{- if .Values.grpcPort }}
      - {name: grpc, containerPort: {{ .Values.grpcPort }}}
      {{- end }}
    env:
      - {name: JAVA_TOOL_OPTIONS, value: {{ .Values.javaOpts | quote }}}
      - {name: SPRING_PROFILES_ACTIVE, value: {{ .Values.profile | quote }}}
      - {name: POD_NAME, valueFrom: {fieldRef: {fieldPath: metadata.name}}}
      - {name: OTEL_SERVICE_NAME, value: {{ include "pj.name" . | quote }}}
      {{- if .Values.otel.enabled }}
      - {name: OTEL_EXPORTER_OTLP_ENDPOINT, value: {{ .Values.otel.endpoint | quote }}}
      - {name: OTEL_TRACES_SAMPLER, value: parentbased_traceidratio}
      - {name: OTEL_TRACES_SAMPLER_ARG, value: {{ .Values.otel.samplingRatio | quote }}}
      {{- end }}
      {{- with .Values.extraEnv }}{{ toYaml . | nindent 6 }}{{- end }}
    envFrom:
      - configMapRef: {name: {{ include "pj.name" . }}}
      {{- if .Values.externalSecret.enabled }}
      - secretRef: {name: {{ include "pj.name" . }}}
      {{- end }}
    resources: {{- toYaml .Values.resources | nindent 6 }}
    securityContext: {{- toYaml .Values.containerSecurityContext | nindent 6 }}
    startupProbe:
      httpGet: {path: {{ .Values.probes.startup.path }}, port: management}
      failureThreshold: {{ .Values.probes.startup.failureThreshold }}
      periodSeconds: {{ .Values.probes.startup.periodSeconds }}
    livenessProbe:
      httpGet: {path: {{ .Values.probes.liveness.path }}, port: management}
      periodSeconds: {{ .Values.probes.liveness.periodSeconds }}
      failureThreshold: {{ .Values.probes.liveness.failureThreshold }}
    readinessProbe:
      httpGet: {path: {{ .Values.probes.readiness.path }}, port: management}
      periodSeconds: {{ .Values.probes.readiness.periodSeconds }}
      failureThreshold: {{ .Values.probes.readiness.failureThreshold }}
    lifecycle:
      preStop:
        exec: {command: ["sleep", "{{ .Values.lifecycle.preStopSleepSeconds }}"]}
    volumeMounts:
      - {name: tmp, mountPath: /tmp}
volumes:
  - {name: tmp, emptyDir: {medium: Memory, sizeLimit: 256Mi}}
{{- end }}
