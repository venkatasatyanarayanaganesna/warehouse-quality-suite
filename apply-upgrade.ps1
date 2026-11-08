# Applies upgrade package A to warehouse-quality-suite.
# Run from the repo root:  powershell -ExecutionPolicy Bypass -File <path>\apply-upgrade.ps1
# Every edit checks that its marker text matches exactly once; if any check fails nothing is written.
# Undo everything with:  git checkout -- . ; git clean -fd .github scripts docs/security

$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = (Get-Location).Path
$utf8 = New-Object System.Text.UTF8Encoding($false)

if (-not (Test-Path (Join-Path $repo 'pom.xml')) -or -not (Test-Path (Join-Path $repo '.git'))) {
  throw "Run this from the warehouse-quality-suite folder (pom.xml and .git were not found in $repo)."
}

function NL([string]$text) { if ($text.Contains("`r`n")) { return "`r`n" } else { return "`n" } }

function Patch([string]$text, [string]$old, [string]$new) {
  $n = ([regex]::Matches($text, [regex]::Escape($old))).Count
  if ($n -ne 1) { throw "Expected exactly one match but found $n for: $old" }
  return $text.Replace($old, $new)
}

function Fit([string]$block, [string]$nl) { return ($block -replace "`r?`n", $nl) }

# ---------------------------------------------------------------- read everything first
$pomPath = Join-Path $repo 'pom.xml'
$composePath = Join-Path $repo 'docker-compose.yml'
$ignorePath = Join-Path $repo '.gitignore'
$readmePath = Join-Path $repo 'README.md'

$pom = [IO.File]::ReadAllText($pomPath)
$compose = [IO.File]::ReadAllText($composePath)
$ignore = [IO.File]::ReadAllText($ignorePath)
$readme = [IO.File]::ReadAllText($readmePath)

# ---------------------------------------------------------------- pom.xml
if ($pom.Contains('jacoco-maven-plugin')) {
  Write-Host 'pom.xml: JaCoCo already present, skipping pom changes.'
} else {
  $nl = NL $pom

  $props = @'
<slf4j.version>2.0.16</slf4j.version>
    <jacoco.version>0.8.14</jacoco.version>
    <!-- Coverage gate for the demo app: report only for now; set jacoco.halt=true once the real number is known -->
    <jacoco.min.line>0.50</jacoco.min.line>
    <jacoco.halt>false</jacoco.halt>
    <!-- empty default so @{argLine} in surefire still resolves when the JaCoCo agent is skipped -->
    <argLine></argLine>
'@
  $pom = Patch $pom '<slf4j.version>2.0.16</slf4j.version>' (Fit $props.TrimEnd() $nl)

  $pom = Patch $pom '--sun-misc-unsafe-memory-access=allow' '@{argLine} --sun-misc-unsafe-memory-access=allow'

  $jacoco = @'
<plugin>
        <groupId>org.jacoco</groupId>
        <artifactId>jacoco-maven-plugin</artifactId>
        <version>${jacoco.version}</version>
        <executions>
          <execution>
            <id>prepare-agent</id>
            <goals><goal>prepare-agent</goal></goals>
          </execution>
          <execution>
            <id>report</id>
            <phase>verify</phase>
            <goals><goal>report</goal></goals>
          </execution>
          <execution>
            <id>check</id>
            <phase>verify</phase>
            <goals><goal>check</goal></goals>
            <configuration>
              <haltOnFailure>${jacoco.halt}</haltOnFailure>
              <rules>
                <rule>
                  <element>BUNDLE</element>
                  <limits>
                    <limit>
                      <counter>LINE</counter>
                      <value>COVEREDRATIO</value>
                      <minimum>${jacoco.min.line}</minimum>
                    </limit>
                  </limits>
                </rule>
              </rules>
            </configuration>
          </execution>
        </executions>
      </plugin>
      <!-- lets you start the demo app with: mvn compile exec:java -->
'@
  $pom = Patch $pom '<!-- lets you start the demo app with: mvn compile exec:java -->' (Fit $jacoco.TrimEnd() $nl)

  $pit = @'
<!-- Experimental mutation testing: mvn -Ppit test-compile org.pitest:pitest-maven:mutationCoverage -->
  <profile>
    <id>pit</id>
    <properties><suiteFile>testng-api.xml</suiteFile></properties>
    <build>
      <plugins>
        <plugin>
          <groupId>org.pitest</groupId>
          <artifactId>pitest-maven</artifactId>
          <version>1.25.7</version>
          <dependencies>
            <dependency>
              <groupId>org.pitest</groupId>
              <artifactId>pitest-testng-plugin</artifactId>
              <version>1.0.0</version>
            </dependency>
          </dependencies>
          <configuration>
            <targetClasses>
              <param>com.ganesna.wms.InventoryStore</param>
              <param>com.ganesna.wms.CsvImport</param>
              <param>com.ganesna.wms.Json</param>
            </targetClasses>
            <targetTests>
              <param>com.ganesna.wms.tests.api.*</param>
            </targetTests>
            <threads>1</threads>
            <timeoutConstant>20000</timeoutConstant>
            <outputFormats>
              <param>HTML</param>
              <param>XML</param>
            </outputFormats>
            <failWhenNoMutations>false</failWhenNoMutations>
          </configuration>
        </plugin>
      </plugins>
    </build>
  </profile>
</profiles>
'@
  $pom = Patch $pom '</profiles>' (Fit $pit.TrimEnd() $nl)
  Write-Host 'pom.xml: JaCoCo (report + gate) and experimental pit profile prepared.'
}

# ---------------------------------------------------------------- docker-compose.yml
if ($compose.Contains('zaproxy')) {
  Write-Host 'docker-compose.yml: ZAP service already present, skipping.'
} else {
  $nl = NL $compose
  $zap = @'

  # Security scan: docker compose --profile security run --rm zap   (report lands in docs/security)
  zap:
    image: ghcr.io/zaproxy/zaproxy:stable
    profiles: ["security"]
    user: root
    depends_on:
      warehouse-app:
        condition: service_healthy
    volumes:
      - ./docs/security:/zap/wrk:rw
    command: zap-baseline.py -t http://warehouse-app:8080 -r zap-report.html -J zap-report.json -I
'@
  if (-not $compose.EndsWith("`n")) { $compose += $nl }
  $compose += (Fit $zap $nl)
  Write-Host 'docker-compose.yml: ZAP baseline service prepared.'
}

# ---------------------------------------------------------------- .gitignore
if (-not $ignore.Contains('docs/security/')) {
  $nl = NL $ignore
  if (-not $ignore.EndsWith("`n")) { $ignore += $nl }
  $ignore += "# generated reports" + $nl + "docs/security/" + $nl + "target/pit-reports/" + $nl
}

# ---------------------------------------------------------------- README.md
if ($readme.Contains('CI, quality gates and tooling')) {
  Write-Host 'README.md: section already present, skipping.'
} else {
  $nl = NL $readme
  $badges = @'

[![CI](https://github.com/venkatasatyanarayanaganesna/warehouse-quality-suite/actions/workflows/ci.yml/badge.svg)](https://github.com/venkatasatyanarayanaganesna/warehouse-quality-suite/actions/workflows/ci.yml)
![Java 25](https://img.shields.io/badge/Java-25-blue)
![Tests](https://img.shields.io/badge/tests-Selenium%20%7C%20REST%20Assured%20%7C%20k6-brightgreen)

'@
  $m = [regex]::Match($readme, '^# [^\r\n]*\r?\n')
  if ($m.Success) { $readme = $readme.Insert($m.Length, (Fit $badges $nl)) }

  $section = @'

## CI, quality gates and tooling

How the pieces fit together:

```mermaid
flowchart LR
  subgraph Suites
    UI[UI tests<br/>Selenium + page objects]
    API[API tests<br/>REST Assured + JSON schema]
    E2E[End-to-end<br/>stock consistency]
    LOAD[k6 load and breakpoint]
  end
  UI --> APP[Warehouse demo app :8080]
  API --> APP
  E2E --> APP
  LOAD --> APP
  ZAP[OWASP ZAP baseline] --> APP
  APP --> COV[JaCoCo line coverage]
  Suites --> RPT[Allure report]
```

| Check | Run locally | In CI |
|---|---|---|
| API suite + line coverage | `mvn -Pci,api verify` (report: `target/site/jacoco/index.html`) | `ci.yml`, job *API tests + coverage* |
| UI + end-to-end suite | `mvn -Pci,ui test` | `ci.yml`, job *UI + end-to-end tests* |
| Allure report | `allure serve target/allure-results` | published to GitHub Pages from `main` |
| Security baseline (OWASP ZAP) | `docker compose up -d --build --wait warehouse-app` then `docker compose --profile security run --rm zap` (report in `docs/security`) | `ci.yml`, job *OWASP ZAP baseline scan* |
| Flaky-test check | n/a | `nightly-flaky.yml` runs the full regression 3 times each week and lists tests whose result changed or needed a retry |
| Mutation testing (experimental) | `mvn -Ppit test-compile org.pitest:pitest-maven:mutationCoverage` | not in CI |

Coverage is measured on the demo app (`src/main`) while the API suite runs. The build reports the figure and compares it with
`jacoco.min.line`; set `-Djacoco.halt=true` to make a drop below the threshold fail the build.

The accounts used by the tests (`admin` / `admin123`, `picker` / `picker123`) belong to the in-memory demo app and are public on purpose.
'@
  if (-not $readme.EndsWith("`n")) { $readme += $nl }
  $readme += (Fit $section $nl)
  Write-Host 'README.md: badges and tooling section prepared.'
}

# ---------------------------------------------------------------- write everything (all checks passed)
foreach ($rel in @('.github\workflows\ci.yml', '.github\workflows\nightly-flaky.yml', 'scripts\flaky_report.py')) {
  $src = Join-Path $here ("files\" + $rel)
  $dst = Join-Path $repo $rel
  New-Item -ItemType Directory -Force -Path (Split-Path -Parent $dst) | Out-Null
  Copy-Item $src $dst -Force
}
New-Item -ItemType Directory -Force -Path (Join-Path $repo 'docs\security') | Out-Null

[IO.File]::WriteAllText($pomPath, $pom, $utf8)
[IO.File]::WriteAllText($composePath, $compose, $utf8)
[IO.File]::WriteAllText($ignorePath, $ignore, $utf8)
[IO.File]::WriteAllText($readmePath, $readme, $utf8)

Write-Host ''
Write-Host 'Upgrade package A applied. Check what changed with:  git status --short ; git diff --stat'
