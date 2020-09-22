
#*******************************************************************************
# SORMAS® - Surveillance Outbreak Response Management & Analysis System
# Copyright © 2016-2018 Helmholtz-Zentrum f�r Infektionsforschung GmbH (HZI)
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.
#*******************************************************************************

#!/bin/bash

echo "# SORMAS TO SORMAS CERTIFICATE IMPORT"
echo "# This script imports a certificate into the local truststore, to be used for SORMAS2SORMAS communication"
echo "# If anything goes wrong, please consult the sormas to sormas import guide or get in touch with the developers."

#if [[ $(expr substr "$(uname -a)" 1 5) = "Linux" ]]; then
LINUX=true
#else
#	LINUX=false
#fi

# DIRECTORIES
if [[ ${LINUX} = true ]]; then
	ROOT_PREFIX=
else
	ROOT_PREFIX=/c
fi
if [[ -z "${SORMAS2SORMAS_DIR}" ]]; then
  DEFAULT_SORMAS2SORMAS_DIR="${ROOT_PREFIX}/opt/sormas2sormas"
  if [[ -d "${DEFAULT_SORMAS2SORMAS_DIR}" ]]; then
    SORMAS2SORMAS_DIR="${DEFAULT_SORMAS2SORMAS_DIR}"
  else
    while [[ ! -d "${SORMAS2SORMAS_DIR}" ]]; do
		  read -r -p "Please specify a valid sormas2sormas directory: " SORMAS2SORMAS_DIR
	  done
	  export SORMAS2SORMAS_DIR
  fi
else
  if [[ ! -d "${SORMAS2SORMAS_DIR}" ]]; then
    echo "sormas2sormas directory not found: ${SORMAS2SORMAS_DIR}"
    exit 1
  fi
fi

if [[ -z "${SORMAS_PROPERTIES}" ]]; then
  DEFAULT_SORMAS_PROPERTIES_PATH="${ROOT_PREFIX}/opt/domains/sormas/sormas.properties"
  if [[ -f "${DEFAULT_SORMAS_PROPERTIES_PATH}" ]]; then
    SORMAS_PROPERTIES="${DEFAULT_SORMAS_PROPERTIES_PATH}"
  else
    while [[ ! -f "${SORMAS_PROPERTIES}" ]]; do
		  read -r -p "Please specify a valid sormas properties path: " SORMAS_PROPERTIES
	  done
	  export SORMAS_PROPERTIES
  fi
else
  if [[ ! -f "${SORMAS_PROPERTIES}" ]]; then
    echo "sormas properties file not found: ${SORMAS_PROPERTIES}"
    exit 1
  fi
fi

ORGANIZATION_LIST_FILE_NAME=organization-list.csv
TRUSTSTORE_FILE_NAME=sormas2sormas.truststore.p12
TRUSTSTORE_FILE=${SORMAS2SORMAS_DIR}/${TRUSTSTORE_FILE_NAME}
ORGANIZATION_LIST_FILE=${SORMAS2SORMAS_DIR}/${ORGANIZATION_LIST_FILE_NAME}
NEW_TRUSTSTORE=false

if [ ! -f "${TRUSTSTORE_FILE}" ]; then
  NEW_TRUSTSTORE=true
  echo "${TRUSTSTORE_FILE_NAME} not found. A new truststore file will be created."
fi

if [ ! -f "${ORGANIZATION_LIST_FILE}" ]; then
  echo "${ORGANIZATION_LIST_FILE_NAME} not found. A new server list file will be created."
  touch "${ORGANIZATION_LIST_FILE}"
fi

while [[ -z "${SORMAS_S2S_TRUSTSTORE_PASS}" ]] || [[ ${#SORMAS_S2S_TRUSTSTORE_PASS} -lt 6 ]]; do
  if [[ ${NEW_TRUSTSTORE} = true ]]; then
    read -sp "Please provide the password for the new truststore (at least 6 characters): " SORMAS_S2S_TRUSTSTORE_PASS
  else
    SORMAS_S2S_TRUSTSTORE_PASS=$(sed -n 's/^sormas2sormas\.truststorePass=//p' "${SORMAS_PROPERTIES}")
    while [[ -z "${SORMAS_S2S_TRUSTSTORE_PASS}" ]] || [[ ${#SORMAS_S2S_TRUSTSTORE_PASS} -lt 6 ]]; do
      read -sp "Please provide the password for the truststore: " SORMAS_S2S_TRUSTSTORE_PASS
    done
  fi
  echo
done

SORMAS_S2S_ORGANIZATION_ID="";
while [[ -z "${SORMAS_S2S_ORGANIZATION_ID}" ]]; do
  read -p "Please provide the organization ID of the certificate owner: " SORMAS_S2S_ORGANIZATION_ID
done

CRT_FILE_NAME=${SORMAS_S2S_ORGANIZATION_ID}.sormas2sormas.cert.crt;
CRT_FILE=${SORMAS2SORMAS_DIR}/${CRT_FILE_NAME}

if [[ ! -f "${CRT_FILE}" ]]; then
  echo "The file ${CRT_FILE_NAME} not found in ${SORMAS2SORMAS_DIR} folder."

  exit 1;
fi

CSV_FILE_NAME=${SORMAS_S2S_ORGANIZATION_ID}-server-access-data.csv;
CSV_FILE=${SORMAS2SORMAS_DIR}/${CSV_FILE_NAME};
if [[ ! -f "${CSV_FILE}" ]]; then
  echo "The file ${CSV_FILE_NAME} not found in ${SORMAS2SORMAS_DIR} folder."

  exit 1;
fi



# import crt
echo "Importing certificate into truststore..."
keytool -importcert -trustcacerts -noprompt -keystore "${TRUSTSTORE_FILE}" -storetype pkcs12 -alias ${SORMAS_S2S_ORGANIZATION_ID} -storepass "${SORMAS_S2S_TRUSTSTORE_PASS}" -file "${CRT_FILE}"

if [[ ${NEW_TRUSTSTORE} = true ]]; then
  # remove existing properties and empty spaces at end of file
  sed -i "/^# SORMAS to SORMAS truststore data/d" "${SORMAS_PROPERTIES}"
  sed -i "/^sormas2sormas\.truststoreName/d" "${SORMAS_PROPERTIES}"
  sed -i "/^sormas2sormas\.truststorePass/d" "${SORMAS_PROPERTIES}"
  sed -i -e :a -e '/^\n*$/{$d;N;};/\n$/ba' "${SORMAS_PROPERTIES}"
  # add new properties
  {
    echo;
    echo "# SORMAS to SORMAS truststore data";
    echo "sormas2sormas.truststoreName=${TRUSTSTORE_FILE_NAME}";
    echo "sormas2sormas.truststorePass=${SORMAS_S2S_TRUSTSTORE_PASS}";
  } >> "${SORMAS_PROPERTIES}"
fi

echo "Updating server list CSV"

( head -1 $CSV_FILE ) >> ${ORGANIZATION_LIST_FILE}

echo "The script finished executing. Please check for any errors."

#openssl s_client -showcerts -servername second.sormas.com -connect second.sormas.com:443 </dev/null | sed -ne '/-BEGIN CERTIFICATE-/,/-END CERTIFICATE-/p' > certificate.cer
# keytool -importcert -trustcacerts -noprompt -keystore ~/Work/payara5_194/glassfish/domains/sormas/config/cacerts.jks -alias secondsormas -storepass changeit -file certificate.cer