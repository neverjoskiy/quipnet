// Package main строит libnovaxray.so — отдельную c-shared библиотеку с
// VLESS/REALITY/XHTTP из вендоренного форка Xray-core.
//
// Почему отдельная библиотека, а не часть quipnet-core:
//
//   - gomobile всегда кладёт в .aar свой libgojni.so и классы пакета `go`,
//     поэтому две gomobile-библиотеки в одном APK конфликтуют;
//   - слить Xray в модуль quipnet-core нельзя: Xray тянет apernet/quic-go и
//     qpack v0.6.0, что ломает quic-go v0.55.0, а его апгрейд ломает
//     connect-ip-go, на котором держится MASQUE.
//
// Отдельный модуль решает обе проблемы: у него собственный граф зависимостей,
// а наружу торчит обычный C ABI.
package main

/*
#include <stdlib.h>

// Колбэк защиты сокета: Java-сторона вызывает VpnService.protect(fd).
// Без него исходящие соединения Xray заворачивались бы обратно в туннель.
typedef int (*quipnet_protect_fn)(int fd);

static int quipnet_invoke_protect(quipnet_protect_fn fn, int fd) {
    if (fn == NULL) {
        return 0;
    }
    return fn(fd);
}
*/
import "C"

import (
	"strings"
	"sync"
	"syscall"
	"unsafe"

	core "github.com/xtls/xray-core/core"
	"github.com/xtls/xray-core/infra/conf/serial"
	"github.com/xtls/xray-core/transport/internet"

	_ "quipnet-xray/registry"
)

var (
	mu       sync.Mutex
	instance *core.Instance
	protect  C.quipnet_protect_fn
)

//export QuipNetXraySetProtector
//
// Принимает указатель на C-функцию, которая передаёт fd в VpnService.protect.
// Передать 0, чтобы отключить.
func QuipNetXraySetProtector(fn C.quipnet_protect_fn) {
	mu.Lock()
	defer mu.Unlock()
	protect = fn

	internet.RegisterDialerController(func(network, address string, conn syscall.RawConn) error {
		mu.Lock()
		callback := protect
		mu.Unlock()
		if callback == nil {
			return nil
		}
		return conn.Control(func(fd uintptr) {
			C.quipnet_invoke_protect(callback, C.int(fd))
		})
	})
}

//export QuipNetXrayStart
//
// Запускает ядро по конфигурации в формате Xray JSON.
// Возвращает пустую строку при успехе или текст ошибки. Строку освобождает
// вызывающая сторона через QuipNetXrayFree.
func QuipNetXrayStart(configJSON *C.char) *C.char {
	mu.Lock()
	defer mu.Unlock()

	if instance != nil {
		return C.CString("xray already running")
	}

	config, err := serial.LoadJSONConfig(strings.NewReader(C.GoString(configJSON)))
	if err != nil {
		return C.CString("config: " + err.Error())
	}
	started, err := core.New(config)
	if err != nil {
		return C.CString("new: " + err.Error())
	}
	if err := started.Start(); err != nil {
		started.Close()
		return C.CString("start: " + err.Error())
	}
	instance = started
	return C.CString("")
}

//export QuipNetXrayStop
func QuipNetXrayStop() {
	mu.Lock()
	defer mu.Unlock()
	if instance == nil {
		return
	}
	instance.Close()
	instance = nil
}

//export QuipNetXrayIsRunning
func QuipNetXrayIsRunning() C.int {
	mu.Lock()
	defer mu.Unlock()
	if instance == nil {
		return 0
	}
	return 1
}

//export QuipNetXrayVersion
func QuipNetXrayVersion() *C.char {
	return C.CString(core.Version())
}

//export QuipNetXrayFree
func QuipNetXrayFree(value *C.char) {
	if value != nil {
		C.free(unsafe.Pointer(value))
	}
}

func main() {}
