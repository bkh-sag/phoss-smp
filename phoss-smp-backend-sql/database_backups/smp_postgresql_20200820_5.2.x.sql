--
-- Copyright (C) 2015-2020 Philip Helger and contributors
-- philip[at]helger[dot]com
--
-- The Original Code is Copyright The Peppol project (http://www.peppol.eu)
--
-- This Source Code Form is subject to the terms of the Mozilla Public
-- License, v. 2.0. If a copy of the MPL was not distributed with this
-- file, You can obtain one at http://mozilla.org/MPL/2.0/.
--

CREATE DATABASE smp;

CREATE TABLE smp_user (
  username varchar(256) NOT NULL,
  password varchar(256) NOT NULL,
  PRIMARY KEY (username),
  UNIQUE (username)
);
INSERT INTO smp_user VALUES ('peppol_user','Test1234');

CREATE TABLE smp_service_group (
  businessIdentifierScheme varchar(25) NOT NULL,
  businessIdentifier varchar(50) NOT NULL,
  extension text,
  PRIMARY KEY (businessIdentifierScheme,businessIdentifier),
  UNIQUE (businessIdentifierScheme,businessIdentifier)
);

CREATE TABLE smp_service_metadata (
  businessIdentifierScheme varchar(25) NOT NULL,
  businessIdentifier varchar(50) NOT NULL,
  documentIdentifierScheme varchar(25) NOT NULL,
  documentIdentifier varchar(500) NOT NULL,
  extension text,
  PRIMARY KEY (businessIdentifierScheme,businessIdentifier,documentIdentifierScheme,documentIdentifier),
  UNIQUE (businessIdentifierScheme,businessIdentifier,documentIdentifierScheme,documentIdentifier),
  CONSTRAINT FK_smp_service_metadata_businessIdentifier FOREIGN KEY (businessIdentifierScheme, businessIdentifier) REFERENCES smp_service_group (businessIdentifierScheme, businessIdentifier) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE smp_process (
  businessIdentifierScheme varchar(25) NOT NULL,
  businessIdentifier varchar(50) NOT NULL,
  documentIdentifierScheme varchar(25) NOT NULL,
  documentIdentifier varchar(500) NOT NULL,
  processIdentifierType varchar(25) NOT NULL,
  processIdentifier varchar(200) NOT NULL,
  extension text,
  PRIMARY KEY (businessIdentifierScheme,businessIdentifier,documentIdentifierScheme,documentIdentifier,processIdentifierType,processIdentifier),
  UNIQUE (businessIdentifierScheme,businessIdentifier,documentIdentifierScheme,documentIdentifier),
  CONSTRAINT FK_smp_process_documentIdentifierScheme FOREIGN KEY (businessIdentifierScheme, businessIdentifier, documentIdentifierScheme, documentIdentifier) REFERENCES smp_service_metadata (businessIdentifierScheme, businessIdentifier, documentIdentifierScheme, documentIdentifier) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE smp_endpoint (
  businessIdentifierScheme varchar(25) NOT NULL,
  businessIdentifier varchar(50) NOT NULL,
  documentIdentifierScheme varchar(25) NOT NULL,
  documentIdentifier varchar(500) NOT NULL,
  processIdentifierType varchar(25) NOT NULL,
  processIdentifier varchar(200) NOT NULL,
  certificate text NOT NULL,
  endpointReference varchar(256) NOT NULL,
  minimumAuthenticationLevel varchar(256) DEFAULT NULL,
  requireBusinessLevelSignature boolean NOT NULL,
  serviceActivationDate timestamp DEFAULT NULL,
  serviceDescription text NOT NULL,
  serviceExpirationDate timestamp DEFAULT NULL,
  technicalContactUrl varchar(256) NOT NULL,
  technicalInformationUrl varchar(256) DEFAULT NULL,
  transportProfile varchar(256) NOT NULL,
  extension text,
  PRIMARY KEY (businessIdentifierScheme,businessIdentifier,documentIdentifierScheme,documentIdentifier,processIdentifierType,processIdentifier,transportProfile),
  CONSTRAINT FK_smp_endpoint_documentIdentifierScheme FOREIGN KEY (businessIdentifierScheme, businessIdentifier, documentIdentifierScheme, documentIdentifier, processIdentifierType, processIdentifier) REFERENCES smp_process (businessIdentifierScheme, businessIdentifier, documentIdentifierScheme, documentIdentifier, processIdentifierType, processIdentifier) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE smp_ownership (
  businessIdentifierScheme varchar(25) NOT NULL,
  businessIdentifier varchar(50) NOT NULL,
  username varchar(256) NOT NULL,
  PRIMARY KEY (businessIdentifierScheme,businessIdentifier,username),
  CONSTRAINT FK_smp_ownership_id FOREIGN KEY (businessIdentifierScheme, businessIdentifier) REFERENCES smp_service_group (businessIdentifierScheme, businessIdentifier) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT FK_smp_ownership_username FOREIGN KEY (username) REFERENCES smp_user (username) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE smp_service_metadata_redirection (
  businessIdentifierScheme varchar(25) NOT NULL,
  businessIdentifier varchar(50) NOT NULL,
  documentIdentifierScheme varchar(25) NOT NULL,
  documentIdentifier varchar(500) NOT NULL,
  certificateUID varchar(256) DEFAULT NULL,
  redirectionUrl varchar(256) NOT NULL,
  extension text,
  certificate text,
  PRIMARY KEY (documentIdentifierScheme,businessIdentifier,businessIdentifierScheme,documentIdentifier),
  CONSTRAINT FK_smp_redirect_businessIdentifier FOREIGN KEY (businessIdentifierScheme, businessIdentifier) REFERENCES smp_service_group (businessIdentifierScheme, businessIdentifier) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE smp_bce (
  id varchar(45) NOT NULL,
  pid varchar(255) NOT NULL,
  name text NOT NULL,
  country varchar(3) NOT NULL,
  geoinfo text,
  identifiers text,
  websites text,
  contacts text,
  addon text,
  regdate date DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE (pid)
);

ALTER TABLE smp_user OWNER to smp;
ALTER TABLE smp_service_group OWNER to smp;
ALTER TABLE smp_service_metadata OWNER to smp;
ALTER TABLE smp_process OWNER to smp;
ALTER TABLE smp_endpoint OWNER to smp;
ALTER TABLE smp_ownership OWNER to smp;
ALTER TABLE smp_service_metadata_redirection OWNER to smp;
ALTER TABLE smp_bce OWNER to smp;