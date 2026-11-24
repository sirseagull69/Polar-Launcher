# ENTWURF, noch nie gebaut. Wird im Gerätebaum eingebunden mit:
#   $(call inherit-product, vendor/polarlicht/polarlicht.mk)

PRODUCT_PACKAGES += \
    Polarlicht

# Boot-Animation aus dem Canvas (Ansichten 1.1 bis 1.6)
PRODUCT_COPY_FILES += \
    vendor/polarlicht/bootanimation.zip:$(TARGET_COPY_OUT_PRODUCT)/media/bootanimation.zip
