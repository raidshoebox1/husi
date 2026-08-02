#!/usr/bin/env bash

buildScript/plugin/easytier/init.sh &&
  buildScript/plugin/easytier/armeabi-v7a.sh &&
  buildScript/plugin/easytier/arm64-v8a.sh &&
  buildScript/plugin/easytier/x86.sh &&
  buildScript/plugin/easytier/x86_64.sh
